package pe.edu.utec.atlasrambackend.service;

import org.springframework.stereotype.Service;
import pe.edu.utec.atlasrambackend.dto.KardexImportResultDTO;
import pe.edu.utec.atlasrambackend.dto.StockConsumptionDTO;
import pe.edu.utec.atlasrambackend.dto.StockReceiptDTO;
import pe.edu.utec.atlasrambackend.dto.StockWriteOffDTO;
import pe.edu.utec.atlasrambackend.exception.InvalidCsvFormatException;
import pe.edu.utec.atlasrambackend.ingest.CsvValues;
import pe.edu.utec.atlasrambackend.model.StockItem;
import pe.edu.utec.atlasrambackend.model.StockLot;
import pe.edu.utec.atlasrambackend.repository.StockItemRepository;
import pe.edu.utec.atlasrambackend.repository.StockLotRepository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Importa el kardex que la farmacia ya lleva (exportado de su sistema o de Excel) a Atlas RAM.
 *
 * <p>Una fila = un movimiento: ingreso de un lote, consumo (dispensación) o baja. Las columnas se reconocen
 * por varios nombres, porque cada sistema de farmacia exporta distinto. Las filas se aplican en orden de
 * fecha y, el mismo día, primero los ingresos: así un archivo ordenado de cualquier forma no deja el stock
 * negativo a mitad de camino. Cada fila se guarda por separado (FEFO y validaciones del kardex manual);
 * una fila con error no detiene las demás y se informa con su número de línea.</p>
 */
@Service
public class KardexImporter {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    enum Field { DATE, TYPE, ANTIBIOTIC, PRESENTATION, QUANTITY, LOT, EXPIRY, NOTE }

    enum Kind { RECEIPT, CONSUMPTION, WRITE_OFF }

    /** Nombres aceptados, normalizados con {@link WhonetFormat#key} (minúsculas, sin tildes ni signos). */
    static final Map<Field, Set<String>> ALIASES = Map.of(
            Field.DATE, Set.of("fecha", "fechamovimiento", "fechadelmovimiento", "date"),
            Field.TYPE, Set.of("tipo", "tipomovimiento", "tipodemovimiento", "movimiento", "type"),
            Field.ANTIBIOTIC, Set.of("codigoantibiotico", "antibiotico", "codigo", "codigomedicamento"),
            Field.PRESENTATION, Set.of("presentacion", "formafarmaceutica"),
            Field.QUANTITY, Set.of("cantidad", "unidades", "qty", "quantity"),
            Field.LOT, Set.of("lote", "numerodelote", "numlote", "lot"),
            Field.EXPIRY, Set.of("vencimiento", "fechavencimiento", "fechadevencimiento", "expiry"),
            Field.NOTE, Set.of("nota", "motivo", "observacion", "observaciones", "servicio"));

    static final Map<String, Kind> KINDS = Map.ofEntries(
            Map.entry("ingreso", Kind.RECEIPT), Map.entry("entrada", Kind.RECEIPT), Map.entry("receipt", Kind.RECEIPT),
            Map.entry("consumo", Kind.CONSUMPTION), Map.entry("salida", Kind.CONSUMPTION),
            Map.entry("dispensacion", Kind.CONSUMPTION), Map.entry("consumption", Kind.CONSUMPTION),
            Map.entry("baja", Kind.WRITE_OFF), Map.entry("merma", Kind.WRITE_OFF),
            Map.entry("vencido", Kind.WRITE_OFF), Map.entry("writeoff", Kind.WRITE_OFF));

    private final StockService stockService;
    private final StockItemRepository itemRepository;
    private final StockLotRepository lotRepository;

    public KardexImporter(StockService stockService, StockItemRepository itemRepository,
                          StockLotRepository lotRepository) {
        this.stockService = stockService;
        this.itemRepository = itemRepository;
        this.lotRepository = lotRepository;
    }

    /** Fila ya leída, con su línea física. */
    record Row(int line, LocalDate date, Kind kind, String antibiotic, String presentation, int quantity,
               String lot, LocalDate expiry, String note) {
    }

    public KardexImportResultDTO importFile(Long facilityId, byte[] content, String userEmail) {
        List<CsvParser.CsvRecord> records = CsvParser.readRecords(content);
        Map<Field, Integer> columns = mapHeader(records.get(0).fields());
        int width = records.get(0).fields().size();
        LocalDate today = LocalDate.now(LIMA);

        List<String> errors = new ArrayList<>();
        List<Row> rows = new ArrayList<>();
        for (CsvParser.CsvRecord record : records.subList(1, records.size())) {
            if (record.fields().size() != width) {
                errors.add("Línea " + record.line() + ": se esperaban " + width + " columnas y llegaron "
                        + record.fields().size());
                continue;
            }
            try {
                rows.add(read(record, columns, today));
            } catch (IllegalArgumentException e) {
                errors.add("Línea " + record.line() + ": " + e.getMessage());
            }
        }

        // Fecha ascendente y, el mismo día, ingresos → consumos → bajas.
        rows.sort(Comparator.comparing(Row::date).thenComparing(Row::kind).thenComparingInt(Row::line));

        Map<String, List<StockItem>> items = new LinkedHashMap<>();
        for (StockItem item : itemRepository.findByFacilityIdAndActiveTrueOrderByIdAsc(facilityId)) {
            items.computeIfAbsent(item.getAntibiotic().getCode().toUpperCase(Locale.ROOT), k -> new ArrayList<>())
                    .add(item);
        }

        Map<Kind, Integer> applied = new EnumMap<>(Kind.class);
        for (Row row : rows) {
            try {
                StockItem item = findItem(items, row);
                switch (row.kind()) {
                    case RECEIPT -> stockService.receive(item.getId(),
                            new StockReceiptDTO(row.lot(), row.expiry(), row.quantity(), row.date()), userEmail);
                    case CONSUMPTION -> stockService.consume(item.getId(),
                            new StockConsumptionDTO(row.quantity(), row.date(), row.note()), userEmail);
                    case WRITE_OFF -> {
                        StockLot lot = lotRepository.findByStockItemIdAndLotNumberIgnoreCase(item.getId(), row.lot())
                                .orElseThrow(() -> new IllegalArgumentException("no existe el lote " + row.lot()
                                        + " de " + row.antibiotic()));
                        stockService.writeOff(lot.getId(), new StockWriteOffDTO(row.quantity(), row.date(),
                                row.note().isBlank() ? "Baja importada del kardex" : row.note()), userEmail);
                    }
                }
                applied.merge(row.kind(), 1, Integer::sum);
            } catch (RuntimeException e) {
                errors.add("Línea " + row.line() + ": " + e.getMessage());
            }
        }

        int processed = applied.values().stream().mapToInt(Integer::intValue).sum();
        errors.sort(Comparator.comparingInt(KardexImporter::lineOf));
        return new KardexImportResultDTO(records.size() - 1, processed, records.size() - 1 - processed,
                applied.getOrDefault(Kind.RECEIPT, 0), applied.getOrDefault(Kind.CONSUMPTION, 0),
                applied.getOrDefault(Kind.WRITE_OFF, 0), errors);
    }

    static Map<Field, Integer> mapHeader(List<String> header) {
        Map<Field, Integer> columns = new EnumMap<>(Field.class);
        for (int i = 0; i < header.size(); i++) {
            String key = WhonetFormat.key(header.get(i));
            for (Map.Entry<Field, Set<String>> alias : ALIASES.entrySet()) {
                if (alias.getValue().contains(key)) {
                    columns.putIfAbsent(alias.getKey(), i);
                }
            }
        }
        List<String> missing = new ArrayList<>();
        for (Field required : List.of(Field.DATE, Field.TYPE, Field.ANTIBIOTIC, Field.QUANTITY)) {
            if (!columns.containsKey(required)) {
                missing.add(required.name().toLowerCase(Locale.ROOT));
            }
        }
        if (!missing.isEmpty()) {
            throw new InvalidCsvFormatException("Faltan columnas del kardex: " + String.join(", ", missing)
                    + ". Se aceptan, por ejemplo: fecha, tipo (INGRESO, CONSUMO, BAJA), codigo_antibiotico, "
                    + "cantidad, lote, vencimiento, presentacion, nota");
        }
        return columns;
    }

    static Row read(CsvParser.CsvRecord record, Map<Field, Integer> columns, LocalDate today) {
        String typeText = get(record, columns, Field.TYPE);
        Kind kind = KINDS.get(WhonetFormat.key(typeText));
        if (kind == null) {
            throw new IllegalArgumentException("tipo de movimiento desconocido '" + typeText
                    + "' (use INGRESO, CONSUMO o BAJA)");
        }
        LocalDate date = date(get(record, columns, Field.DATE), today, "fecha");
        String antibiotic = get(record, columns, Field.ANTIBIOTIC).toUpperCase(Locale.ROOT);
        if (antibiotic.isEmpty()) {
            throw new IllegalArgumentException("falta el código del antibiótico");
        }
        int quantity = quantity(get(record, columns, Field.QUANTITY));
        if (quantity <= 0) {
            throw new IllegalArgumentException("la cantidad debe ser mayor que cero");
        }
        String lot = get(record, columns, Field.LOT);
        LocalDate expiry = null;
        if (kind != Kind.CONSUMPTION && lot.isEmpty()) {
            throw new IllegalArgumentException("un " + (kind == Kind.RECEIPT ? "ingreso" : "baja")
                    + " necesita el número de lote");
        }
        if (kind == Kind.RECEIPT) {
            String rawExpiry = get(record, columns, Field.EXPIRY);
            if (rawExpiry.isEmpty()) {
                throw new IllegalArgumentException("un ingreso necesita la fecha de vencimiento del lote");
            }
            // El vencimiento puede ser futuro: se valida el formato con una fecha de referencia lejana.
            expiry = date(rawExpiry, LocalDate.of(9999, 12, 31), "vencimiento");
        }
        return new Row(record.line(), date, kind, antibiotic, get(record, columns, Field.PRESENTATION), quantity,
                lot, expiry, get(record, columns, Field.NOTE));
    }

    /** Mismos formatos que la carga de aislamientos, con el nombre de la columna correcto en el error. */
    private static LocalDate date(String raw, LocalDate today, String column) {
        try {
            return CsvValues.parseDate(raw, today);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(e.getMessage().replace("fecha_toma", column));
        }
    }

    /** Entero positivo de unidades ("120" o "120.0"); "12.5" o "doce" se rechazan. */
    static int quantity(String raw) {
        try {
            double value = Double.parseDouble(raw.replace(',', '.'));
            if (value != Math.rint(value) || value > Integer.MAX_VALUE) {
                throw new NumberFormatException();
            }
            return (int) value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("cantidad inválida '" + raw + "' (se espera un entero de unidades)");
        }
    }

    private static StockItem findItem(Map<String, List<StockItem>> items, Row row) {
        List<StockItem> candidates = items.getOrDefault(row.antibiotic(), List.of());
        if (!row.presentation().isEmpty()) {
            candidates = candidates.stream()
                    .filter(i -> i.getPresentation().equalsIgnoreCase(row.presentation())).toList();
        }
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("el establecimiento no tiene un ítem activo de " + row.antibiotic()
                    + (row.presentation().isEmpty() ? "" : " (" + row.presentation() + ")")
                    + "; créelo primero en el inventario");
        }
        if (candidates.size() > 1) {
            throw new IllegalArgumentException(row.antibiotic() + " tiene varias presentaciones; indique la "
                    + "columna presentacion");
        }
        return candidates.get(0);
    }

    private static String get(CsvParser.CsvRecord record, Map<Field, Integer> columns, Field field) {
        Integer index = columns.get(field);
        return index == null ? "" : record.fields().get(index).trim();
    }

    private static int lineOf(String error) {
        try {
            return Integer.parseInt(error.substring(6, error.indexOf(':')));
        } catch (RuntimeException e) {
            return Integer.MAX_VALUE;
        }
    }
}

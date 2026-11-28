package pe.edu.utec.atlasrambackend.access;

import pe.edu.utec.atlasrambackend.model.FacilitySector;

import java.time.Instant;
import java.util.List;

/** Pedido de establecimiento con lo que el sistema comprobó y el sector que decide RENIPRESS. */
public record FacilityRequestResponseDTO(
        Long id,
        String requesterEmail,
        String name,
        String ruc,
        String renipressCode,
        String districtName,
        FacilitySector claimedSector,
        FacilitySector sectorByRenipress,
        String renipressName,
        String renipressInstitution,
        AccessRequestStatus status,
        Instant createdAt,
        String note,
        Long facilityId,
        List<String> checks
) {}

package pe.edu.utec.atlasrambackend.alert;

/**
 * Resultado de la prueba: proporción observada en la ventana, proporción esperada según la
 * línea base y probabilidad de ver algo tan extremo si nada hubiera cambiado.
 */
public record OutbreakSignal(double observedRate, double expectedRate, double pValue) {
}

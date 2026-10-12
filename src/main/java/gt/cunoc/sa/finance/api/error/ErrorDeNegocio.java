package gt.cunoc.sa.finance.api.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Error esperado de un caso de uso: lleva el estado HTTP y un detalle en lenguaje claro que
 * el frontend muestra tal cual. Una sola clase con fábricas en vez de una jerarquía: lo único
 * que cambia entre un 404 y un 409 es el estado.
 */
public class ErrorDeNegocio extends RuntimeException {

    private final HttpStatus estado;

    private ErrorDeNegocio(HttpStatus estado, String detalle) {
        super(detalle);
        this.estado = estado;
    }

    public static ErrorDeNegocio solicitudInvalida(String detalle) {
        return new ErrorDeNegocio(HttpStatus.BAD_REQUEST, detalle);
    }

    public static ErrorDeNegocio noAutenticado(String detalle) {
        return new ErrorDeNegocio(HttpStatus.UNAUTHORIZED, detalle);
    }

    public static ErrorDeNegocio noEncontrado(String detalle) {
        return new ErrorDeNegocio(HttpStatus.NOT_FOUND, detalle);
    }

    public static ErrorDeNegocio conflicto(String detalle) {
        return new ErrorDeNegocio(HttpStatus.CONFLICT, detalle);
    }

    public static ErrorDeNegocio noProcesable(String detalle) {
        return new ErrorDeNegocio(HttpStatus.UNPROCESSABLE_ENTITY, detalle);
    }

    /** El 4xx de otro servicio (S6), con su mismo estado y su detail. */
    public static ErrorDeNegocio conEstado(HttpStatusCode estado, String detalle) {
        return new ErrorDeNegocio(HttpStatus.valueOf(estado.value()), detalle);
    }

    public HttpStatus getEstado() {
        return estado;
    }
}

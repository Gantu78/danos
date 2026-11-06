namespace Danos.Api.Dominio;

// Los nombres van tal cual se escriben en el JSON y en la base ("SIN_DANO"), así no hace falta
// traducir entre el texto del contrato y el código. Se guardan como texto (HasConversion<string>).

public enum NivelDano
{
    SIN_DANO,
    LEVE,
    MODERADO,
    GRAVE,
    DESTRUCCION_TOTAL
}

public enum EstadoEvaluacion
{
    PRELIMINAR,
    REGISTRADA,
    EN_CONFLICTO,
    VALIDADA,
    RECHAZADA
}

public enum TipoConflicto
{
    VERSION,
    DUPLICADO
}

public enum EstadoConflicto
{
    PENDIENTE,
    RESUELTO
}

public enum TipoOperacion
{
    CREAR,
    ACTUALIZAR
}

public enum ResultadoOperacion
{
    APLICADA,
    EN_CONFLICTO,
    POSIBLE_DUPLICADO,
    INVALIDA
}

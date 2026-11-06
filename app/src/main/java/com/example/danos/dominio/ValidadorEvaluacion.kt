package com.example.danos.dominio

enum class CampoEvaluacion {
    ACTIVO, INSTANTE, EVALUADOR, ORGANIZACION, UBICACION, PRECISION, TIPO_ACTIVO, NIVEL_DANO, DESCRIPCION,
}

data class ErrorCampo(val campo: CampoEvaluacion, val mensaje: String)

object ValidadorEvaluacion {

    fun validar(evaluacion: Evaluacion): List<ErrorCampo> = buildList {
        fun exigir(valor: String, campo: CampoEvaluacion, mensaje: String) {
            if (valor.isBlank()) add(ErrorCampo(campo, mensaje))
        }
        exigir(evaluacion.activoId, CampoEvaluacion.ACTIVO, "Elige un activo")
        exigir(evaluacion.evaluador, CampoEvaluacion.EVALUADOR, "Falta el evaluador")
        exigir(evaluacion.organizacion, CampoEvaluacion.ORGANIZACION, "Falta la organización")
        exigir(evaluacion.tipoActivoCodigo, CampoEvaluacion.TIPO_ACTIVO, "Elige el tipo de activo")
        exigir(evaluacion.descripcion, CampoEvaluacion.DESCRIPCION, "La descripción es obligatoria")
        fun limitar(valor: String, maximo: Int, campo: CampoEvaluacion) {
            if (valor.length > maximo) add(ErrorCampo(campo, "Máximo $maximo caracteres"))
        }
        limitar(evaluacion.evaluador, Limites.NOMBRE, CampoEvaluacion.EVALUADOR)
        limitar(evaluacion.organizacion, Limites.NOMBRE, CampoEvaluacion.ORGANIZACION)
        limitar(evaluacion.descripcion, Limites.DESCRIPCION, CampoEvaluacion.DESCRIPCION)
        if (evaluacion.nivelDano == null) {
            add(ErrorCampo(CampoEvaluacion.NIVEL_DANO, "Elige el nivel de daño"))
        }
        val latValida = evaluacion.lat in -90.0..90.0
        val lonValida = evaluacion.lon in -180.0..180.0
        if (!latValida || !lonValida) {
            add(ErrorCampo(CampoEvaluacion.UBICACION, "Ubicación fuera de rango"))
        }
        if (evaluacion.precisionM.isNaN() || evaluacion.precisionM < 0.0) {
            add(ErrorCampo(CampoEvaluacion.PRECISION, "Precisión de ubicación no válida"))
        }
    }
}

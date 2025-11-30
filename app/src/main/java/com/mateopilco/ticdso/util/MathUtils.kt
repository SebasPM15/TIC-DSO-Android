package com.mateopilco.ticdso.util

import android.opengl.Matrix

/**
 * Representa la posición y rotación de la cámara en el mundo.
 * Usamos una matriz de 16 floats (estándar de OpenGL, column-major).
 */
data class CameraPose(
    val matrix: FloatArray
) {
    // === PROPIEDADES ACCESIBLES PARA LA UI ===
    // En matrices 4x4 OpenGL (column-major), la traslación está en los índices 12, 13, 14.
    val tx: Float get() = matrix[12]
    val ty: Float get() = matrix[13]
    val tz: Float get() = matrix[14]

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CameraPose
        return matrix.contentEquals(other.matrix)
    }

    override fun hashCode(): Int = matrix.contentHashCode()

    companion object {
        fun identity(): CameraPose {
            val m = FloatArray(16)
            Matrix.setIdentityM(m, 0)
            return CameraPose(m)
        }
    }
}

object MathUtils {

    /**
     * Convierte formato TUM/Engel (tx, ty, tz, qx, qy, qz, qw) a Matriz 4x4.
     */
    fun poseFromQuaternion(
        tx: Float, ty: Float, tz: Float,
        qx: Float, qy: Float, qz: Float, qw: Float
    ): CameraPose {
        val m = FloatArray(16)

        // Conversión Matemática de Cuaternión a Matriz de Rotación
        val xx = qx * qx; val xy = qx * qy; val xz = qx * qz; val xw = qx * qw
        val yy = qy * qy; val yz = qy * qz; val yw = qy * qw
        val zz = qz * qz; val zw = qz * qw

        // Matriz de Rotación 3x3 insertada en 4x4 (Column-Major)
        // Columna 0
        m[0] = 1 - 2 * (yy + zz)
        m[1] = 2 * (xy + zw)
        m[2] = 2 * (xz - yw)
        m[3] = 0f

        // Columna 1
        m[4] = 2 * (xy - zw)
        m[5] = 1 - 2 * (xx + zz)
        m[6] = 2 * (yz + xw)
        m[7] = 0f

        // Columna 2
        m[8] = 2 * (xz + yw)
        m[9] = 2 * (yz - xw)
        m[10] = 1 - 2 * (xx + yy)
        m[11] = 0f

        // Columna 3 (Traslación)
        m[12] = tx
        m[13] = ty
        m[14] = tz
        m[15] = 1f

        return CameraPose(m)
    }

    /**
     * Aplica la transformación de la cámara a un punto local.
     */
    fun transformPoint(point: Point3D, pose: CameraPose): Point3D {
        val vecIn = floatArrayOf(point.x, point.y, point.z, 1f)
        val vecOut = FloatArray(4)

        // Multiplicación Matriz x Vector
        Matrix.multiplyMV(vecOut, 0, pose.matrix, 0, vecIn, 0)

        return point.copy(
            x = vecOut[0],
            y = vecOut[1],
            z = vecOut[2]
        )
    }
}
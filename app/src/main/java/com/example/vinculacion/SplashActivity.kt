package com.example.vinculacion

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

/**
 * Actividad de splash screen con emoji de ave
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private companion object {
        const val SPLASH_DURATION = 2000L // 2 segundos
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Configurar barra de estado para que coincida con el fondo
        window.statusBarColor = getColor(R.color.primary_color)
        window.navigationBarColor = getColor(R.color.primary_color)

        // Navegar a MainActivity después del tiempo especificado
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, SPLASH_DURATION)
    }
}

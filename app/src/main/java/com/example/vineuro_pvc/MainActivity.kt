package com.example.vineuro_pvc

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import com.example.vineuro_pvc.data.BloqueoApp
import android.view.MotionEvent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.example.vineuro_pvc.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private var pidiendoDesbloqueo = false

    private val desbloqueo = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        pidiendoDesbloqueo = false
        if (r.resultCode == RESULT_OK) {
            BloqueoApp.marcarDesbloqueado()
            binding.root.visibility = android.view.View.VISIBLE
        } else {
            finishAffinity()   // sin identificarse no se muestra nada de la app
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        com.example.vineuro_pvc.data.Ajustes.aplicarTema(this)
        com.example.vineuro_pvc.data.Unidades.cargar(this)
        super.onCreate(savedInstanceState)
        com.example.vineuro_pvc.data.AutoSync.iniciar(this)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            // Barras del sistema + muesca de la cámara (en horizontal) + teclado: nada queda tapado ni fuera de la pantalla
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val teclado = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(barras.left, barras.top, barras.right, maxOf(barras.bottom, teclado.bottom))
            insets
        }
        setSupportActionBar(binding.toolbar)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        val navController = navHostFragment.navController

        // Carga los colores de perfil de Precios para dibujar las ventanas en su color
        com.example.vineuro_pvc.data.PreciosRepository(this).obtener()

        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)
    }

    override fun onStart() {
        super.onStart()
        // A5 (Plan de Seguridad): bloqueo opcional con el PIN, patrón o huella del teléfono
        if (BloqueoApp.debePedir(this)) {
            binding.root.visibility = android.view.View.INVISIBLE
            if (!pidiendoDesbloqueo) {
                val intent = BloqueoApp.intentoDesbloqueo(this, getString(R.string.bloqueo_pedir_titulo), getString(R.string.bloqueo_pedir_desc))
                if (intent != null) {
                    pidiendoDesbloqueo = true
                    desbloqueo.launch(intent)
                }
            }
        } else if (!pidiendoDesbloqueo) {
            binding.root.visibility = android.view.View.VISIBLE
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !pidiendoDesbloqueo) BloqueoApp.marcarSalida()
    }

    /** Anota cada toque para medir el tiempo que de verdad se trabaja en una cotización. */
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        com.example.vineuro_pvc.data.ActividadUsuario.registrarToque()
        return super.dispatchTouchEvent(ev)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        com.example.vineuro_pvc.data.ActividadUsuario.registrarFoco(hasFocus)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}

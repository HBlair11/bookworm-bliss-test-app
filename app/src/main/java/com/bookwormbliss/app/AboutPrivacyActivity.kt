package com.bookwormbliss.app
import com.bookwormbliss.app.core.theme.ThemeController

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bookwormbliss.app.util.SystemBarController

class AboutPrivacyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeController.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about_privacy)
        SystemBarController.apply(this)
        // Pad content below the status bar and above the nav bar so the
        // layout doesn't overlap the system bars.
        val root = findViewById<android.view.View>(R.id.aboutRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
        // Patch v37: standard MaterialToolbar back navigation.
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.aboutToolbar)
            .setNavigationOnClickListener { finish() }
    }
}

package com.josxtm.autocontrol.community

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class MaterialMotionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun awaitReady() {
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("Procesando en el dispositivo…").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test fun navigateBetweenAnimatedSectionsAndReturn() {
        awaitReady()
        compose.onNodeWithText("Registros", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Diario", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Nueva entrada").assertExists()
        compose.onNodeWithText("Documentos", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Nuevo texto en blanco").assertExists()
        compose.onNodeWithText("Registros", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Buscar registro").assertExists()
    }

    @Test fun quickActionsAndBackupHelpCanExpandAndCollapse() {
        awaitReady()
        compose.onNodeWithText("Accesos rápidos").performClick()
        compose.onNodeWithText("Ir a Copias").performClick()
        compose.onNodeWithText("Copias cifradas").assertExists()
        compose.onNodeWithText("Ocultar información de seguridad").performClick()
        compose.onNodeWithText("Mostrar información de seguridad").assertExists()
        compose.onNodeWithText("Mostrar información de seguridad").performClick()
        compose.onNodeWithText("Ocultar información de seguridad").assertExists()
    }
}

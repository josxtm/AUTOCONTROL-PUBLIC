package com.josxtm.autocontrol.community

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A compact, operational right-hand tool rail shared by all non-network modules.
 * Every interactive item delegates to an existing WorkspaceModel operation or ActivityResult launcher.
 */
@Composable
fun IndustrialModuleTools(
    tab: Int,
    model: WorkspaceModel,
    enabled: Boolean,
    importDocument: () -> Unit,
    exportDocument: () -> Unit,
    importOcrModel: () -> Unit,
    recognizeImage: () -> Unit,
    deleteDocument: () -> Unit,
    backup: () -> Unit,
    restore: () -> Unit,
    openDocuments: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = when (tab) {
        1 -> "DIARIO · HERRAMIENTAS"
        2 -> "DOCUMENTOS · ACCIONES"
        3 -> "COPIAS · SEGURIDAD"
        5 -> "CONFIGURACIÓN · TEMAS"
        6 -> "PES / BDI · DOCUMENTOS"
        else -> "INICIO · ACCESOS"
    }
    IndustrialPanel(title, "settings", modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)) {
            when (tab) {
                1 -> {
                    Text("Entrada del diario", style = MaterialTheme.typography.labelMedium)
                    Text("Caracteres: " + model.journalText.length, style = MaterialTheme.typography.bodySmall)
                    IndustrialButton("Guardar entrada", "document",
                        enabled && model.journalText.isNotBlank(), true) { model.addJournal() }
                    IndustrialButton("Entradas recientes", "search", enabled) { model.diaryPage(true) }
                    IndustrialButton("Página siguiente", "document",
                        enabled && model.journal.isNotEmpty()) { model.diaryPage(false) }
                    HorizontalDivider()
                    Text("Entradas mostradas: " + model.journal.size,
                        style = MaterialTheme.typography.bodySmall)
                }
                2 -> {
                    IndustrialButton("Importar documento", "document", enabled) { importDocument() }
                    IndustrialButton("Nuevo texto", "document", enabled) { model.newDocument() }
                    IndustrialButton("Guardar texto", "status",
                        enabled && model.docEditable && model.docTitle.isNotBlank(), true) {
                        model.saveDocument()
                    }
                    IndustrialButton("Exportar documento", "document",
                        enabled && model.docId != null) { exportDocument() }
                    IndustrialButton("Eliminar documento", "clear",
                        enabled && model.docId != null) { deleteDocument() }
                    HorizontalDivider()
                    Text("Documentos locales: " + model.documents.size,
                        style = MaterialTheme.typography.bodySmall)
                    Text("OCR LOCAL", style = MaterialTheme.typography.labelMedium)
                    IndustrialButton("Importar modelo OCR", "document", enabled) { importOcrModel() }
                    IndustrialButton("Reconocer imagen", "search",
                        enabled && model.ocrReady) { recognizeImage() }
                    Text(if (model.ocrReady) "Modelo OCR listo" else "Modelo OCR no importado",
                        style = MaterialTheme.typography.bodySmall)
                }
                3 -> {
                    Text("Introduce la contraseña en el formulario del módulo antes de continuar.",
                        style = MaterialTheme.typography.bodySmall)
                    IndustrialButton("Exportar copia cifrada", "lock", enabled, true) { backup() }
                    IndustrialButton("Restaurar copia cifrada", "database", enabled) { restore() }
                    HorizontalDivider()
                    Text("Catálogo y documentos almacenados localmente.",
                        style = MaterialTheme.typography.bodySmall)
                    Text("No se guarda ni se recupera la contraseña de la copia.",
                        style = MaterialTheme.typography.bodySmall)
                }
                5 -> {
                    Text("TEMA SELECCIONADO", style = MaterialTheme.typography.labelMedium)
                    Text(autoThemes[model.tabletTheme].name, style = MaterialTheme.typography.titleSmall)
                    HorizontalDivider()
                    autoThemes.forEachIndexed { index, theme ->
                        IndustrialButton(
                            (if (model.tabletTheme == index) "✓ " else "") + theme.name,
                            "settings", enabled, model.tabletTheme == index
                        ) { model.selectTabletTheme(index) }
                    }
                }
                6 -> {
                    Text("Documentación local", style = MaterialTheme.typography.labelMedium)
                    IndustrialButton("Abrir documentos", "document", enabled) { openDocuments() }
                    HorizontalDivider()
                    Text("El motor PES/BDI definitivo aún no está disponible en esta edición. No se simulan exportaciones ni validaciones.",
                        style = MaterialTheme.typography.bodySmall)
                }
                else -> {
                    Text("Estado de AutoControL", style = MaterialTheme.typography.labelMedium)
                    Text(model.message, style = MaterialTheme.typography.bodySmall)
                    IndustrialButton("Abrir documentos", "document", enabled) { openDocuments() }
                    Text("Los diagnósticos de red y las operaciones de CT están en sus módulos respectivos.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

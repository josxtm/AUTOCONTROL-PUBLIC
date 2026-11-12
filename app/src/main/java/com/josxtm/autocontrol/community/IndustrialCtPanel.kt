package com.josxtm.autocontrol.community

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext

/** Tablet CT workbench: existing local catalog/search/note behavior, no data migration. */
@Composable
fun IndustrialCtPanel(
    model: WorkspaceModel,
    enabled: Boolean,
    importCatalog: () -> Unit,
    chooseFields: () -> Unit,
    openSniffer: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth >= 760.dp) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IndustrialPanel("BUSCADOR Y CATÁLOGO CT", "search", Modifier.weight(.37f).fillMaxHeight()) {
                    CtSearchTools(model, enabled, importCatalog, chooseFields,
                        Modifier.weight(1f).fillMaxWidth(), true)
                }
                IndustrialPanel("FICHA CT Y HERRAMIENTAS", "ct", Modifier.weight(.63f).fillMaxHeight()) {
                    CtRecordDetails(model, enabled, Modifier.weight(1f).fillMaxWidth(), true, openSniffer)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                IndustrialPanel("BUSCADOR Y CATÁLOGO CT", "search", Modifier.fillMaxWidth()) {
                    CtSearchTools(model, enabled, importCatalog, chooseFields, Modifier.fillMaxWidth(), false)
                }
                IndustrialPanel("FICHA CT Y HERRAMIENTAS", "ct", Modifier.fillMaxWidth()) {
                    CtRecordDetails(model, enabled, Modifier.fillMaxWidth(), false, openSniffer)
                }
            }
        }
    }
}

@Composable
private fun CtSearchTools(
    model: WorkspaceModel, enabled: Boolean, importCatalog: () -> Unit,
    chooseFields: () -> Unit, modifier: Modifier, scrollable: Boolean
) {
    Column(modifier.then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("CENTROS DE TRANSFORMACIÓN", style = MaterialTheme.typography.titleMedium)
        Text("Catálogo privado · solo almacenamiento local", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        Text("ESTADO DE IMPORTACIÓN", style = MaterialTheme.typography.labelMedium)
        Surface(color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
            Text(model.importReport.ifBlank { model.message },
                modifier = Modifier.padding(10.dp),
                style = MaterialTheme.typography.bodySmall)
        }
        if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        OutlinedTextField(model.identifier, { model.identifier = it.take(128) },
            modifier = Modifier.fillMaxWidth(), label = { Text("Campo identificador") },
            singleLine = true, enabled = enabled)
        Button(onClick = importCatalog, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text("Importar catálogo JSON")
        }
        HorizontalDivider()
        OutlinedTextField(model.query, { model.query = it.take(256) },
            modifier = Modifier.fillMaxWidth(), label = { Text("Matrícula / identificador") },
            singleLine = true, enabled = enabled)
        Button(onClick = model::search, enabled = enabled && model.query.isNotBlank(),
            modifier = Modifier.fillMaxWidth()) { Text("Buscar CT") }
        OutlinedButton(onClick = chooseFields, enabled = enabled && model.fields.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()) { Text("Seleccionar campos visibles") }
        HorizontalDivider()
        Text(if (model.activeId == null) "Sin CT seleccionado"
            else "CT seleccionado: " + model.activeId,
            style = MaterialTheme.typography.labelMedium)
        Text("La IP LAN se obtiene del registro local seleccionado para SNIFFER.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CtRecordDetails(model: WorkspaceModel, enabled: Boolean, modifier: Modifier, scrollable: Boolean, openSniffer: () -> Unit) {
    val context = LocalContext.current
    Column(modifier.then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("FICHA DEL CT", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f))
            Text(if (model.activeId == null) "SIN SELECCIÓN" else "REGISTRO LOCAL",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        HorizontalDivider()
        if (model.activeId == null) {
            Text("Busca una matrícula para abrir la ficha, sus campos y anotaciones.",
                style = MaterialTheme.typography.bodyMedium)
        } else {
            model.record.filterKeys { it in model.visible }.forEach { (field, value) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top) {
                    Text(field, Modifier.weight(.4f), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, softWrap = true)
                    Text(value ?: "(vacío)", Modifier.weight(.6f),
                        style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider()
            }
            Text("ACCIONES RÁPIDAS", style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IndustrialButton("Abrir SNIFFER", "network", enabled, true) { openSniffer() }
                val address = listOfNotNull(
                    model.record.entries.firstOrNull { it.key.equals("CALLE", true) }?.value,
                    model.record.entries.firstOrNull { it.key.equals("MUNICIPIO", true) }?.value
                ).filter { it.isNotBlank() }.joinToString(", ")
                IndustrialButton("Ver en Maps", "network", enabled && address.isNotBlank()) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(address)))
                    try { context.startActivity(intent) }
                    catch (_: android.content.ActivityNotFoundException) { }
                }
            }
            Text("RED LOCAL", style = MaterialTheme.typography.labelMedium)
            Text(model.selectedCtLan ?: "IP LAN no disponible para este CT",
                style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("NOTAS DEL CT", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(model.note, { model.note = it.take(16000) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 115.dp),
                label = { Text("Anotaciones") }, enabled = enabled)
            Button(onClick = model::saveNote, enabled = enabled,
                modifier = Modifier.fillMaxWidth()) { Text("Guardar nota del CT") }
        }
    }
}

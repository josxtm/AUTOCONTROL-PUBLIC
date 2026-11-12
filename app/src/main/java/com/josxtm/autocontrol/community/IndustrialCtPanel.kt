package com.josxtm.autocontrol.community

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow

/** Tablet CT workbench: existing local catalog/search/note behavior, no data migration. */
@Composable
fun IndustrialCtPanel(
    model: WorkspaceModel,
    enabled: Boolean,
    importCatalog: () -> Unit,
    chooseFields: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth >= 760.dp) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(Modifier.weight(.37f).fillMaxHeight(), shape = MaterialTheme.shapes.medium) {
                    CtSearchTools(model, enabled, importCatalog, chooseFields, Modifier.fillMaxSize())
                }
                Card(Modifier.weight(.63f).fillMaxHeight(), shape = MaterialTheme.shapes.medium) {
                    CtRecordDetails(model, enabled, Modifier.fillMaxSize())
                }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(Modifier.fillMaxWidth()) {
                    CtSearchTools(model, enabled, importCatalog, chooseFields, Modifier.fillMaxWidth())
                }
                Card(Modifier.fillMaxWidth()) {
                    CtRecordDetails(model, enabled, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun CtSearchTools(
    model: WorkspaceModel, enabled: Boolean, importCatalog: () -> Unit,
    chooseFields: () -> Unit, modifier: Modifier
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("CENTROS DE TRANSFORMACIÓN", style = MaterialTheme.typography.titleMedium)
        Text("Catálogo privado · solo almacenamiento local", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
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
private fun CtRecordDetails(model: WorkspaceModel, enabled: Boolean, modifier: Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(14.dp),
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                    Text(value ?: "(vacío)", Modifier.weight(.6f),
                        style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider()
            }
            Text("NOTAS DEL CT", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(model.note, { model.note = it.take(16000) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 115.dp),
                label = { Text("Anotaciones") }, enabled = enabled)
            Button(onClick = model::saveNote, enabled = enabled,
                modifier = Modifier.fillMaxWidth()) { Text("Guardar nota del CT") }
        }
    }
}

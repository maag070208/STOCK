package com.axzydev.stock.api.modules.labels

import java.sql.Connection
import java.util.UUID

data class SeedLabel(val key: String, val nameEs: String, val nameEn: String, val zpl: String)

// The label templates a new database starts with (labels.md section 4). Their layouts are the spec's examples, for a
// 4 × 2 inch roll at 203 dpi, until the owner confirms the roll (labels.md, question 1).
object SeedLabels {
	val all = listOf(
		SeedLabel(
			"PRODUCT", "Etiqueta de producto", "Product label",
			"""
			^XA
			^PW812
			^LL406
			^LH0,0
			^CF0,40
			^FO30,30^FB752,2,0,L,0^FD{{name}}^FS
			^FO30,150^BY3,2,110^BCN,110,Y,N,N^FD{{barcode}}^FS
			^FO30,320^A0N,40,40^FD{{code}}  {{unit}}^FS
			^PQ{{copies}}
			^XZ
			""".trimIndent(),
		),
		SeedLabel(
			"LOCATION", "Etiqueta de ubicación", "Location label",
			"""
			^XA
			^PW812
			^LL406
			^LH0,0
			^FO30,20^A0N,110,100^FD{{code}}^FS
			^FO30,170^BY3,2,110^BCN,110,Y,N,N^FD{{code}}^FS
			^FO470,40^A0N,40,40^FD{{warehouse_code}}^FS
			^FO470,110^A0N,36,36^FDPasillo {{aisle}}^FS
			^FO470,160^A0N,36,36^FDRack {{rack}}^FS
			^FO470,210^A0N,36,36^FDNivel {{level}}^FS
			^PQ{{copies}}
			^XZ
			""".trimIndent(),
		),
	)
}

// Adds the templates that are missing, as version 1, and never changes one that exists.
class LabelSeeder(private val connection: Connection) {
	fun seed(labels: List<SeedLabel>) {
		for (label in labels) {
			val id = UUID.randomUUID()
			val inserted = connection.prepareStatement(
				"insert into label_templates (id, key, name_es, name_en, version, created_at, updated_at) values (?, ?, ?, ?, 1, now(), now()) on conflict (key) do nothing",
			).use {
				it.setObject(1, id)
				it.setString(2, label.key)
				it.setString(3, label.nameEs)
				it.setString(4, label.nameEn)
				it.executeUpdate()
			}
			if (inserted == 0) continue
			connection.prepareStatement("insert into label_template_versions (template_id, version, zpl, created_at) values (?, 1, ?, now())").use {
				it.setObject(1, id)
				it.setString(2, label.zpl)
				it.executeUpdate()
			}
		}
	}
}

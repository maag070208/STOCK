package db.migration

import com.axzydev.stock.api.modules.labels.LabelSeeder
import com.axzydev.stock.api.modules.labels.SeedLabels
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.util.zip.CRC32

// Seeds the label templates from the code. It runs again whenever the seed changes, and it only adds what is missing.
@Suppress("ClassName")
class R__Seed_labels : BaseJavaMigration() {

	override fun getChecksum(): Int = CRC32().apply { update(SeedLabels.all.toString().toByteArray()) }.value.toInt()

	override fun migrate(context: Context) {
		LabelSeeder(context.connection).seed(SeedLabels.all)
	}
}

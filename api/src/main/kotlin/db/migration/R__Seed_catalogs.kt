package db.migration

import com.axzydev.stock.api.modules.catalogs.CatalogSeeder
import com.axzydev.stock.api.modules.catalogs.SeedCatalogs
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.util.zip.CRC32

// Seeds the value catalogs from the code. It runs again whenever the seed changes, and it only adds what is missing.
@Suppress("ClassName")
class R__Seed_catalogs : BaseJavaMigration() {

	override fun getChecksum(): Int = CRC32().apply { update(SeedCatalogs.all.toString().toByteArray()) }.value.toInt()

	override fun migrate(context: Context) {
		CatalogSeeder(context.connection).seed(SeedCatalogs.all)
	}
}

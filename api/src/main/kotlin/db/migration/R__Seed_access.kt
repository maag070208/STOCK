package db.migration

import com.axzydev.stock.api.modules.identity.access.AccessSeeder
import com.axzydev.stock.api.modules.identity.access.PermissionCatalog
import com.axzydev.stock.api.modules.identity.access.SeedRoles
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.util.zip.CRC32

// Seeds the roles and the permission catalog from the code. It is a repeatable migration:
// it runs again whenever the catalog in the code changes, and it never overwrites what an administrator changed.
@Suppress("ClassName")
class R__Seed_access : BaseJavaMigration() {

	override fun getChecksum(): Int =
		CRC32().apply { update((SeedRoles.all.toString() + PermissionCatalog.all.toString()).toByteArray()) }.value.toInt()

	override fun migrate(context: Context) {
		AccessSeeder(context.connection).seed(SeedRoles.all, PermissionCatalog.all)
	}
}

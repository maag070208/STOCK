package com.axzydev.stock.api.modules.orders

import com.axzydev.stock.api.core.errors.ApiException
import com.axzydev.stock.api.core.errors.ErrorCode
import com.axzydev.stock.api.core.security.CurrentUser
import com.axzydev.stock.api.core.security.Scope
import com.axzydev.stock.api.modules.catalogs.CatalogService
import com.axzydev.stock.api.modules.identity.users.UserService
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

// One alert as the app and the web show it: the order it is about, and why.
data class OrderAlertView(
	val id: UUID,
	val order: OrderView,
	val kind: String,
	val raisedOn: LocalDate,
)

data class OrderAlertsResponse(val alerts: List<OrderAlertView>)

// The due date alerts of the orders (order-fulfillment.md section 7a): every open order whose due date has come, or is
// tomorrow, raises one alert a day for the assigned operator, the people who validate it in its warehouse, and — from
// the due date on — its creator. A day is never raised twice, and the unique key is what makes that true.
@Service
class OrderAlertService(
	private val alerts: OrderAlertRepository,
	private val orders: OrderRepository,
	private val views: OrderViews,
	private val people: UserService,
	private val catalogs: CatalogService,
	private val jdbc: JdbcTemplate,
	private val clock: Clock,
) {
	// The daily check: every open order raises today's alerts. Answers how many were raised.
	@Transactional
	fun run(): Int = raise(dueToday(), today())

	// The alerts of the signed-in person, with the ones of today raised first when the day's check has not run yet.
	@Transactional
	fun mine(user: CurrentUser, language: String): OrderAlertsResponse {
		if (!user.can(OrderService.VIEW)) throw ApiException(ErrorCode.INSUFFICIENT_PERMISSIONS)
		val today = today()
		val mine = dueToday().filter { user.scopeOf(OrderService.VIEW) == Scope.ALL || it.warehouseId in user.warehouseIds }
		raise(mine, today)
		val rows = alerts.findByUserIdOrderByAlertDateDescDueDateAsc(user.id)
		val orderById = orders.findAllById(rows.map { it.orderId }.toSet()).associateBy { it.id!! }
		val headers = views.headers(orderById.values.toList(), language).associateBy { it.id }
		return OrderAlertsResponse(
			rows.mapNotNull { row ->
				headers[row.orderId]?.let { header -> OrderAlertView(row.id!!, header, row.kind, row.alertDate) }
			},
		)
	}

	// The open orders whose due date is today, past, or tomorrow.
	private fun dueToday(): List<Order> {
		val open = listOf(statusId("VALIDATED"), statusId("CANCELLED"))
		return orders.findDueBy(today().plusDays(1), open)
	}

	// Writes the alerts of the day that are not written yet. The insert ignores a row that already exists, so two checks
	// on the same day, or two of them at the same moment, write one.
	private fun raise(due: List<Order>, today: LocalDate): Int {
		val validators = due.map { it.warehouseId }.distinct().associateWith { warehouseId ->
			people.peopleInWarehouse(warehouseId)
				.filter { it.access.reaches(OrderService.VALIDATE, warehouseId) }
				.map { it.id }
		}
		var raised = 0
		for (order in due) {
			val kind = OrderAlertRules.due(order.dueDate, today) ?: continue
			val told = OrderAlertRules.recipients(kind, order.operatorId, validators.getValue(order.warehouseId), order.createdBy)
			for (userId in told) {
				raised += jdbc.update(
					"""
					insert into order_alerts (id, order_id, user_id, alert_date, due_date, kind, created_at) values (?, ?, ?, ?, ?, ?, ?)
					on conflict (order_id, user_id, alert_date) do nothing
					""".trimIndent(),
					UUID.randomUUID(), order.id, userId, today, order.dueDate, kind.name, Timestamp.from(clock.instant()),
				)
			}
		}
		return raised
	}

	private fun statusId(code: String): UUID =
		catalogs.itemId(OrderService.STATUS_CATALOG, code) ?: error("ORDER_STATUS has no $code")

	private fun today(): LocalDate = LocalDate.ofInstant(clock.instant(), ZoneId.of(OrderService.BUSINESS_ZONE))
}

// The daily run of the alert check, a few minutes after midnight in the business zone (order-fulfillment.md section 7a).
@Component
class OrderAlertSchedule(private val alerts: OrderAlertService) {

	@Scheduled(cron = "0 5 0 * * *", zone = OrderService.BUSINESS_ZONE)
	fun daily() {
		alerts.run()
	}
}

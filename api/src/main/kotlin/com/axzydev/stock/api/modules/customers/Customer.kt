package com.axzydev.stock.api.modules.customers

import com.axzydev.stock.api.modules.partners.Address
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "customers")
class Customer(
	var code: String,
	var name: String,
	var taxId: String,
	@Embedded var address: Address,
	var paymentTermsId: UUID,
	var createdBy: UUID?,
	var createdAt: Instant,
	var updatedAt: Instant,
) {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	var id: UUID? = null

	var active: Boolean = true
}

@Entity
@Table(name = "customer_contacts")
class CustomerContact(
	var customerId: UUID,
	var name: String,
	var email: String?,
	var phone: String?,
	var createdBy: UUID?,
	var createdAt: Instant,
	var updatedAt: Instant,
) {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	var id: UUID? = null

	var active: Boolean = true
}

interface CustomerRepository : JpaRepository<Customer, UUID>, JpaSpecificationExecutor<Customer> {
	fun findByCode(code: String): Customer?
}

interface CustomerContactRepository : JpaRepository<CustomerContact, UUID> {
	fun findByCustomerIdOrderByName(customerId: UUID): List<CustomerContact>

	fun findByIdAndCustomerId(id: UUID, customerId: UUID): CustomerContact?
}

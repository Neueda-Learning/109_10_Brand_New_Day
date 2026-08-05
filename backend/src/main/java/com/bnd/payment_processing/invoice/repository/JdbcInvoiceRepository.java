package com.bnd.payment_processing.invoice.repository;

import com.bnd.payment_processing.invoice.model.Invoice;
import com.bnd.payment_processing.invoice.model.InvoiceStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring JDBC implementation of {@link InvoiceRepository} (no JPA/Hibernate,
 * per spec.md Section 4).
 */
@Repository
public class JdbcInvoiceRepository implements InvoiceRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcInvoiceRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Invoice insert(Invoice invoice) {
        String sql = """
            INSERT INTO invoices
            (id, invoice_number, customer_id, product_name, product_code, credit_units,
             subtotal_amount, gst_amount, total_amount, currency, status, created_at, updated_at)
            VALUES (:id, :invoiceNumber, :customerId, :productName, :productCode, :creditUnits,
                    :subtotalAmount, :gstAmount, :totalAmount, :currency, :status, :createdAt, :updatedAt)
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", invoice.getId().toString())
                .addValue("invoiceNumber", invoice.getInvoiceNumber())
                .addValue("customerId", invoice.getCustomerId().toString())
                .addValue("productName", invoice.getProductName())
                .addValue("productCode", invoice.getProductCode())
                .addValue("creditUnits", invoice.getCreditUnits())
                .addValue("subtotalAmount", invoice.getSubtotalAmount())
                .addValue("gstAmount", invoice.getGstAmount())
                .addValue("totalAmount", invoice.getTotalAmount())
                .addValue("currency", invoice.getCurrency())
                .addValue("status", invoice.getStatus().name())
                .addValue("createdAt", Timestamp.from(invoice.getCreatedAt()))
                .addValue("updatedAt", Timestamp.from(invoice.getUpdatedAt()));

        jdbcTemplate.update(sql, params);
        return invoice;
    }

    @Override
    public Optional<Invoice> findById(UUID id) {
        String sql = "SELECT * FROM invoices WHERE id = :id";
        List<Invoice> results = jdbcTemplate.query(sql, new MapSqlParameterSource("id", id.toString()), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public Optional<Invoice> findByInvoiceNumber(String invoiceNumber) {
        String sql = "SELECT * FROM invoices WHERE invoice_number = :invoiceNumber";
        List<Invoice> results = jdbcTemplate.query(
                sql, new MapSqlParameterSource("invoiceNumber", invoiceNumber), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public int updateStatusIfCurrent(UUID id, InvoiceStatus expectedCurrentStatus, InvoiceStatus newStatus) {
        String sql = """
            UPDATE invoices
            SET status = :newStatus, updated_at = :updatedAt
            WHERE id = :id AND status = :expectedCurrentStatus
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("newStatus", newStatus.name())
                .addValue("updatedAt", Timestamp.from(java.time.Instant.now()))
                .addValue("id", id.toString())
                .addValue("expectedCurrentStatus", expectedCurrentStatus.name());

        return jdbcTemplate.update(sql, params);
    }

    private Invoice mapRow(ResultSet rs, int rowNum) throws SQLException {
        Invoice invoice = new Invoice();
        invoice.setId(UUID.fromString(rs.getString("id")));
        invoice.setInvoiceNumber(rs.getString("invoice_number"));
        invoice.setCustomerId(UUID.fromString(rs.getString("customer_id")));
        invoice.setProductName(rs.getString("product_name"));
        invoice.setProductCode(rs.getString("product_code"));
        invoice.setCreditUnits(rs.getInt("credit_units"));
        invoice.setSubtotalAmount(rs.getBigDecimal("subtotal_amount"));
        invoice.setGstAmount(rs.getBigDecimal("gst_amount"));
        invoice.setTotalAmount(rs.getBigDecimal("total_amount"));
        invoice.setCurrency(rs.getString("currency"));
        invoice.setStatus(InvoiceStatus.valueOf(rs.getString("status")));
        invoice.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        invoice.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());
        return invoice;
    }
}

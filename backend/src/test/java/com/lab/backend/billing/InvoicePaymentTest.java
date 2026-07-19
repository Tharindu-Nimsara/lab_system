package com.lab.backend.billing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Payment status + balance derivation on the Invoice entity. */
class InvoicePaymentTest {

    private Invoice invoice(String total) {
        Invoice inv = new Invoice();
        inv.setTotal(new BigDecimal(total));
        inv.setAmountPaid(BigDecimal.ZERO);
        return inv;
    }

    @Test
    void unpaidWhenNothingPaid() {
        Invoice inv = invoice("1000");
        inv.recomputeStatus();
        assertThat(inv.getStatus()).isEqualTo("UNPAID");
        assertThat(inv.getBalance()).isEqualByComparingTo("1000");
    }

    @Test
    void partialWhenDepositPaid() {
        Invoice inv = invoice("1000");
        inv.setAmountPaid(new BigDecimal("400"));
        inv.recomputeStatus();
        assertThat(inv.getStatus()).isEqualTo("PARTIAL");
        assertThat(inv.getBalance()).isEqualByComparingTo("600");
    }

    @Test
    void paidWhenFullyPaid() {
        Invoice inv = invoice("1000");
        inv.setAmountPaid(new BigDecimal("1000"));
        inv.recomputeStatus();
        assertThat(inv.getStatus()).isEqualTo("PAID");
        assertThat(inv.getBalance()).isEqualByComparingTo("0");
    }

    @Test
    void settlingABalanceFlipsPartialToPaid() {
        Invoice inv = invoice("1000");
        inv.setAmountPaid(new BigDecimal("400"));
        inv.recomputeStatus();
        // later payment of the remaining balance
        inv.setAmountPaid(inv.getAmountPaid().add(inv.getBalance()));
        inv.recomputeStatus();
        assertThat(inv.getStatus()).isEqualTo("PAID");
        assertThat(inv.getBalance()).isEqualByComparingTo("0");
    }

    @Test
    void voidStaysVoidRegardlessOfPayment() {
        Invoice inv = invoice("1000");
        inv.setStatus("VOID");
        inv.setAmountPaid(new BigDecimal("1000"));
        inv.recomputeStatus();
        assertThat(inv.getStatus()).isEqualTo("VOID");
    }
}

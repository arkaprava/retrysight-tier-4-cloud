/*
 * Copyright (c) 2026 Arka Prava Ghosh
 * SPDX-License-Identifier: Apache-2.0
 */

package com.agentretry.manager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "manager.cost")
public class CostFeatureProperties {

    private boolean enterpriseEnabled = false;
    private boolean billingApiEnabled = false;
    private String cursorBillingApiUrl = "";
    private String cursorBillingApiKey = "";

    public boolean isEnterpriseEnabled() {
        return enterpriseEnabled;
    }

    public void setEnterpriseEnabled(boolean enterpriseEnabled) {
        this.enterpriseEnabled = enterpriseEnabled;
    }

    public boolean isBillingApiEnabled() {
        return billingApiEnabled;
    }

    public void setBillingApiEnabled(boolean billingApiEnabled) {
        this.billingApiEnabled = billingApiEnabled;
    }

    public String getCursorBillingApiUrl() {
        return cursorBillingApiUrl;
    }

    public void setCursorBillingApiUrl(String cursorBillingApiUrl) {
        this.cursorBillingApiUrl = cursorBillingApiUrl;
    }

    public String getCursorBillingApiKey() {
        return cursorBillingApiKey;
    }

    public void setCursorBillingApiKey(String cursorBillingApiKey) {
        this.cursorBillingApiKey = cursorBillingApiKey;
    }

    public boolean isBillingReconciliationAvailable() {
        return enterpriseEnabled;
    }
}

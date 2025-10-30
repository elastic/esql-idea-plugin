/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package co.elastic.grammar;

public class EsqlConfig {

    // versioning information
    boolean devVersion = false;

    public boolean isDevVersion() {
        return devVersion;
    }

    boolean isReleaseVersion() {
        return isDevVersion() == false;
    }

    public void setDevVersion(boolean dev) {
        this.devVersion = dev;
    }
}

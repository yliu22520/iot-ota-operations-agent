package com.yliu22520.iotota.diagnosis;

public interface RetryPlanPort {

    RetryPlanningResult evaluateAndCreate(RetryPlanRequest request);
}

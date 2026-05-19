package com.payperless.visaslot.model;

import java.util.List;

public record CapacityResponse(List<Appointment> appointments, List<Appointment> exceptions) {

  public static CapacityResponse empty() {
    return new CapacityResponse(List.of(), List.of());
  }
}

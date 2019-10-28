package com.parkit.parkingsystem.service;

import com.parkit.parkingsystem.constants.Fare;
import com.parkit.parkingsystem.model.Ticket;

import static java.time.temporal.ChronoUnit.SECONDS;

public class FareCalculatorService {

    public void calculateFare(Ticket ticket) {
        if ((ticket.getOutTime() == null) || (ticket.getOutTime().before(ticket.getInTime()))) {
            throw new IllegalArgumentException("Out time provided is incorrect:" + ticket.getOutTime().toString());
        }

        double duration = SECONDS.between(ticket.getInTime().toInstant(), ticket.getOutTime().toInstant());

        switch (ticket.getParkingSpot().getParkingType()) {
            case CAR: {
                ticket.setPrice(duration > 1800 ? duration / 3600.0 * Fare.CAR_RATE_PER_HOUR : 0);
                break;
            }
            case BIKE: {
                ticket.setPrice(duration > 1800 ? duration / 3600.0 * Fare.BIKE_RATE_PER_HOUR : 0);
                break;
            }
            default:
                throw new IllegalArgumentException("Unkown Parking Type");
        }
        if (ticket.isDiscount())
            ticket.setPrice(ticket.getPrice() * 0.95);
    }
}
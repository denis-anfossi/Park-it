package com.parkit.parkingsystem.integration;

import com.parkit.parkingsystem.constants.Fare;
import com.parkit.parkingsystem.constants.ParkingType;
import com.parkit.parkingsystem.dao.ParkingSpotDAO;
import com.parkit.parkingsystem.dao.TicketDAO;
import com.parkit.parkingsystem.integration.config.DataBaseTestConfig;
import com.parkit.parkingsystem.integration.service.DataBasePrepareService;
import com.parkit.parkingsystem.model.ParkingSpot;
import com.parkit.parkingsystem.model.Ticket;
import com.parkit.parkingsystem.service.ParkingService;
import com.parkit.parkingsystem.util.InputReaderUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ParkingDataBaseIT {

    private static DataBaseTestConfig dataBaseTestConfig = new DataBaseTestConfig();
    @Spy
    private static ParkingSpotDAO parkingSpotDAO;
    @Spy
    private static TicketDAO ticketDAO;
    private static DataBasePrepareService dataBasePrepareService;

    @Mock
    private static InputReaderUtil inputReaderUtil;

    private Ticket ticket;

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @BeforeAll
    private static void setUp() throws Exception {
        parkingSpotDAO = new ParkingSpotDAO();
        parkingSpotDAO.dataBaseConfig = dataBaseTestConfig;
        ticketDAO = new TicketDAO();
        ticketDAO.dataBaseConfig = dataBaseTestConfig;
        dataBasePrepareService = new DataBasePrepareService();
    }

    @AfterAll
    private static void tearDown() {

    }

    @BeforeEach
    private void setUpPerTest() throws Exception {
        when(inputReaderUtil.readSelection()).thenReturn(1);
        when(inputReaderUtil.readVehicleRegistrationNumber()).thenReturn("ABCDEF");
        dataBasePrepareService.clearDataBaseEntries();
    }

    @Test
    public void testParkingACar() {
        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);
        Date dateBefore = new Date();
        try {
            TimeUnit.SECONDS.sleep(1);
            parkingService.processIncomingVehicle();
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Date dateAfter = new Date();

        verify(parkingSpotDAO, Mockito.times(1)).updateParking(any(ParkingSpot.class));
        assertEquals(2, parkingSpotDAO.getNextAvailableSlot(ParkingType.CAR));

        verify(ticketDAO, Mockito.times(1)).saveTicket(any(Ticket.class));
        ticket = ticketDAO.getTicket("ABCDEF");
        assertEquals(1, ticket.getId());
        assertEquals(1, ticket.getParkingSpot().getId());
        assertEquals("ABCDEF", ticket.getVehicleRegNumber());
        assertEquals(0.0, ticket.getPrice());
        assertNotNull(ticket.getInTime());
        assertTrue(ticket.getInTime().after(dateBefore));
        assertTrue(ticket.getInTime().before(dateAfter));
        assertNull(ticket.getOutTime());
    }

    @Test
    public void testParkingLotExit() {
        testParkingACar();
        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);
        Date dateBefore = new Date();
        try {
            TimeUnit.SECONDS.sleep(1);
            parkingService.processExitingVehicle();
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Date dateAfter = new Date();

        verify(ticketDAO, Mockito.times(1)).updateTicket(any(Ticket.class));
        Ticket newTicket = ticketDAO.getTicket("ABCDEF");
        assertEquals(ticket.getId(), newTicket.getId());
        assertEquals(ticket.getParkingSpot(), newTicket.getParkingSpot());
        assertEquals(ticket.getVehicleRegNumber(), newTicket.getVehicleRegNumber());
        assertEquals(ticket.getInTime(), newTicket.getInTime());
        assertNotNull(newTicket.getOutTime());
        assertTrue(newTicket.getOutTime().after(dateBefore));
        assertTrue(newTicket.getOutTime().before(dateAfter));
        double duration = SECONDS.between(newTicket.getInTime().toInstant(), newTicket.getOutTime().toInstant());
        assertEquals(duration > 1800 ? duration / 3600.0 * Fare.CAR_RATE_PER_HOUR : 0, newTicket.getPrice());

        verify(parkingSpotDAO, Mockito.times(2)).updateParking(any(ParkingSpot.class));
        assertEquals(1, parkingSpotDAO.getNextAvailableSlot(ParkingType.CAR));
    }

    @Test
    public void testParkingARecurringCar() {
        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);
        setUpStreams();
        parkingService.processIncomingVehicle();
        assertFalse(outContent.toString().contains("Welcome back! As a recurring user of our parking lot, you'll benefit from a 5% discount."));
        restoreStreams();
        ticket = ticketDAO.getTicket("ABCDEF");
        assertFalse(ticket.isDiscount());
        try {
            TimeUnit.SECONDS.sleep(1);
            parkingService.processExitingVehicle();
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        setUpStreams();
        parkingService.processIncomingVehicle();
        assertTrue(outContent.toString().contains("Welcome back! As a recurring user of our parking lot, you'll benefit from a 5% discount."));

        ticket = ticketDAO.getTicket("ABCDEF");
        assertTrue(ticket.isDiscount());
    }

    public void setUpStreams() {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    public void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }


}

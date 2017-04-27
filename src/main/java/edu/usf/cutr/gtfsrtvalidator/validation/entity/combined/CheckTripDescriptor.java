/*
 * Copyright (C) 2017 University of South Florida.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.usf.cutr.gtfsrtvalidator.validation.entity.combined;

import com.google.transit.realtime.GtfsRealtime;
import edu.usf.cutr.gtfsrtvalidator.api.model.MessageLogModel;
import edu.usf.cutr.gtfsrtvalidator.api.model.OccurrenceModel;
import edu.usf.cutr.gtfsrtvalidator.background.GtfsMetadata;
import edu.usf.cutr.gtfsrtvalidator.helper.ErrorListHelperModel;
import edu.usf.cutr.gtfsrtvalidator.util.TimestampUtils;
import edu.usf.cutr.gtfsrtvalidator.validation.interfaces.FeedEntityValidator;
import org.hsqldb.lib.StringUtil;
import org.onebusaway.gtfs.impl.GtfsDaoImpl;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static edu.usf.cutr.gtfsrtvalidator.util.GtfsUtils.isAddedTrip;
import static edu.usf.cutr.gtfsrtvalidator.validation.ValidationRules.*;

/**
 * E003 - All trip_ids provided in the GTFS-rt feed must appear in the GTFS data
 * (unless schedule_relationship is ADDED)
 *
 * E004 - All route_ids provided in the GTFS-rt feed must appear in the GTFS data
 *
 * E016 - trip_ids with schedule_relationship ADDED must not be in GTFS data
 *
 * E020 - Invalid start_time format
 *
 * E021 - Invalid start_date format
 *
 * E023 - start_time does not match GTFS initial arrival_time
 *
 * W006 - trip_update missing trip_id
 */
public class CheckTripDescriptor implements FeedEntityValidator {

    private static final org.slf4j.Logger _log = LoggerFactory.getLogger(CheckTripDescriptor.class);

    @Override
    public List<ErrorListHelperModel> validate(long currentTimeMillis, GtfsDaoImpl gtfsData, GtfsMetadata gtfsMetadata, GtfsRealtime.FeedMessage feedMessage, GtfsRealtime.FeedMessage previousFeedMessage) {
        List<OccurrenceModel> errorListE003 = new ArrayList<>();
        List<OccurrenceModel> errorListE004 = new ArrayList<>();
        List<OccurrenceModel> errorListE016 = new ArrayList<>();
        List<OccurrenceModel> errorListE020 = new ArrayList<>();
        List<OccurrenceModel> errorListE021 = new ArrayList<>();
        List<OccurrenceModel> errorListE023 = new ArrayList<>();
        List<OccurrenceModel> errorListW006 = new ArrayList<>();

        // Check the route_id values against the values from the GTFS feed
        for (GtfsRealtime.FeedEntity entity : feedMessage.getEntityList()) {
            if (entity.hasTripUpdate()) {
                GtfsRealtime.TripUpdate tripUpdate = entity.getTripUpdate();
                if (!tripUpdate.getTrip().hasTripId()) {
                    // W006 - No trip_id
                    OccurrenceModel om = new OccurrenceModel("entity ID " + entity.getId());
                    errorListW006.add(om);
                    _log.debug(om.getPrefix() + " " + W006.getOccurrenceSuffix());
                } else {
                    String tripId = tripUpdate.getTrip().getTripId();
                    if (!gtfsMetadata.getTripIds().contains(tripId)) {
                        if (!isAddedTrip(tripUpdate.getTrip())) {
                            // Trip isn't in GTFS data and isn't an ADDED trip - E003
                            OccurrenceModel om = new OccurrenceModel("trip_id " + tripId);
                            errorListE003.add(om);
                            _log.debug(om.getPrefix() + " " + E003.getOccurrenceSuffix());
                        }
                    } else {
                        if (isAddedTrip(tripUpdate.getTrip())) {
                            // Trip is in GTFS data and is an ADDED trip - E016
                            OccurrenceModel om = new OccurrenceModel("trip_id " + tripId);
                            errorListE016.add(om);
                            _log.debug(om.getPrefix() + " " + E016.getOccurrenceSuffix());
                        }
                    }
                }

                if (tripUpdate.getTrip().hasStartTime()) {
                    String startTime = tripUpdate.getTrip().getStartTime();
                    if (!TimestampUtils.isValidTimeFormat(startTime)) {
                        // E020 - Invalid start_time format
                        OccurrenceModel om = new OccurrenceModel("trip_id " + tripUpdate.getTrip().getTripId() + " start_time is " + startTime);
                        errorListE020.add(om);
                        _log.debug(om.getPrefix() + " " + E020.getOccurrenceSuffix());
                    }
                    String tripId = tripUpdate.getTrip().getTripId();
                    if (tripId != null && !gtfsMetadata.getExactTimesZeroTripIds().contains(tripId) && !gtfsMetadata.getExactTimesOneTrips().containsKey(tripId)) {
                        // Trip is a normal (not frequencies.txt) trip
                        int firstArrivalTime = gtfsMetadata.getTripStopTimes().get(tripId).get(0).getArrivalTime();
                        String formattedArrivalTime = TimestampUtils.posixToClock(firstArrivalTime);
                        if (!startTime.equals(formattedArrivalTime)) {
                            // E023 - start_time does not match GTFS initial arrival_time
                            OccurrenceModel om = new OccurrenceModel("GTFS-rt trip_id " + tripId + " start_time is " + startTime + " and GTFS initial arrival_time is " + formattedArrivalTime);
                            errorListE020.add(om);
                            _log.debug(om.getPrefix() + " " + E023.getOccurrenceSuffix());
                        }
                    }
                }

                if (tripUpdate.getTrip().hasStartDate()) {
                    if (!TimestampUtils.isValidDateFormat(tripUpdate.getTrip().getStartDate())) {
                        // E021 - Invalid start_date format
                        OccurrenceModel om = new OccurrenceModel("trip_id " + tripUpdate.getTrip().getTripId() + " start_date is " + tripUpdate.getTrip().getStartDate());
                        errorListE021.add(om);
                        _log.debug(om.getPrefix() + " " + E021.getOccurrenceSuffix());
                    }
                }

                String routeId = tripUpdate.getTrip().getRouteId();
                if (!StringUtil.isEmpty(routeId) && !gtfsMetadata.getRouteIds().contains(routeId)) {
                    // E004 - route_id not in GTFS data
                    OccurrenceModel om = new OccurrenceModel("route_id " + routeId);
                    errorListE004.add(om);
                    _log.debug(om.getPrefix() + " " + E004.getOccurrenceSuffix());
                }
            }
            if (entity.hasVehicle() && entity.getVehicle().hasTrip()) {
                GtfsRealtime.TripDescriptor tripDescriptor = entity.getVehicle().getTrip();
                if (!tripDescriptor.hasTripId()) {
                    // W006 - No trip_id
                    OccurrenceModel om = new OccurrenceModel("entity ID " + entity.getId());
                    errorListW006.add(om);
                    _log.debug(om.getPrefix() + " " + W006.getOccurrenceSuffix());
                } else {
                    String tripId = tripDescriptor.getTripId();
                    if (!StringUtil.isEmpty(tripId)) {
                        if (!gtfsMetadata.getTripIds().contains(tripId)) {
                            if (!isAddedTrip(tripDescriptor)) {
                                // Trip isn't in GTFS data and isn't an ADDED trip - E003
                                OccurrenceModel om = new OccurrenceModel("vehicle_id " + entity.getVehicle().getVehicle().getId() + " trip_id " + tripId);
                                errorListE003.add(om);
                                _log.debug(om.getPrefix() + " " + E003.getOccurrenceSuffix());
                            }
                        } else {
                            if (isAddedTrip(tripDescriptor)) {
                                // Trip is in GTFS data and is an ADDED trip - E016
                                OccurrenceModel om = new OccurrenceModel("vehicle_id " + entity.getVehicle().getVehicle().getId() + " trip_id " + tripId);
                                errorListE016.add(om);
                                _log.debug(om.getPrefix() + " " + E016.getOccurrenceSuffix());
                            }
                        }
                    }
                }

                if (tripDescriptor.hasStartTime()) {
                    String startTime = tripDescriptor.getStartTime();
                    if (!TimestampUtils.isValidTimeFormat(startTime)) {
                        // E020 - Invalid start_time format
                        OccurrenceModel om = new OccurrenceModel("vehicle_id " + entity.getVehicle().getVehicle().getId() + " trip_id " + tripDescriptor.getTripId() + " start_time is " + startTime);
                        errorListE020.add(om);
                        _log.debug(om.getPrefix() + " " + E020.getOccurrenceSuffix());
                    }
                    String tripId = tripDescriptor.getTripId();
                    if (tripId != null && !gtfsMetadata.getExactTimesZeroTripIds().contains(tripId) && !gtfsMetadata.getExactTimesOneTrips().containsKey(tripId)) {
                        // Trip is a normal (not frequencies.txt) trip
                        int firstArrivalTime = gtfsMetadata.getTripStopTimes().get(tripId).get(0).getArrivalTime();
                        String formattedArrivalTime = TimestampUtils.posixToClock(firstArrivalTime);
                        if (!startTime.equals(formattedArrivalTime)) {
                            // E023 - start_time does not match GTFS initial arrival_time
                            OccurrenceModel om = new OccurrenceModel("GTFS-rt trip_id " + tripId + " start_time is " + startTime + " and GTFS initial arrival_time is " + formattedArrivalTime);
                            errorListE020.add(om);
                            _log.debug(om.getPrefix() + " " + E023.getOccurrenceSuffix());
                        }
                    }
                }

                if (tripDescriptor.hasStartDate()) {
                    if (!TimestampUtils.isValidDateFormat(tripDescriptor.getStartDate())) {
                        // E021 - Invalid start_date format
                        OccurrenceModel om = new OccurrenceModel("vehicle_id " + entity.getVehicle().getVehicle().getId() + " trip_id " + tripDescriptor.getTripId() + " start_date is " + tripDescriptor.getStartDate());
                        errorListE021.add(om);
                        _log.debug(om.getPrefix() + " " + E021.getOccurrenceSuffix());
                    }
                }

                String routeId = entity.getVehicle().getTrip().getRouteId();
                if (!StringUtil.isEmpty(routeId) && !gtfsMetadata.getRouteIds().contains(routeId)) {
                    // E004 - route_id not in GTFS data
                    OccurrenceModel om = new OccurrenceModel("vehicle_id " + entity.getVehicle().getVehicle().getId() + " route_id " + routeId);
                    errorListE004.add(om);
                    _log.debug(om.getPrefix() + " " + E004.getOccurrenceSuffix());
                }
            }
        }
        List<ErrorListHelperModel> errors = new ArrayList<>();
        if (!errorListE003.isEmpty()) {
            errors.add(new ErrorListHelperModel(new MessageLogModel(E003), errorListE003));
        }
        if (!errorListE004.isEmpty()) {
            errors.add(new ErrorListHelperModel(new MessageLogModel(E004), errorListE004));
        }
        if (!errorListE016.isEmpty()) {
            errors.add(new ErrorListHelperModel(new MessageLogModel(E016), errorListE016));
        }
        if (!errorListE020.isEmpty()) {
            errors.add(new ErrorListHelperModel(new MessageLogModel(E020), errorListE020));
        }
        if (!errorListE021.isEmpty()) {
            errors.add(new ErrorListHelperModel(new MessageLogModel(E021), errorListE021));
        }
        if (!errorListE023.isEmpty()) {
            errors.add(new ErrorListHelperModel(new MessageLogModel(E023), errorListE023));
        }
        if (!errorListW006.isEmpty()) {
            errors.add(new ErrorListHelperModel(new MessageLogModel(W006), errorListW006));
        }
        return errors;
    }
}

/**
 * Copyright (C) 2013-2017 52°North Initiative for Geospatial Open Source
 * Software GmbH
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License version 2 as publishedby the Free
 * Software Foundation.
 *
 * If the program is linked with libraries which are licensed under one of the
 * following licenses, the combination of the program with the linked library is
 * not considered a "derivative work" of the program:
 *
 *     - Apache License, version 2.0
 *     - Apache Software License, version 1.0
 *     - GNU Lesser General Public License, version 3
 *     - Mozilla Public License, versions 1.0, 1.1 and 2.0
 *     - Common Development and Distribution License (CDDL), version 1.0
 *
 * Therefore the distribution of the program linked with libraries licensed under
 * the aforementioned licenses, is permitted by the copyright holders if the
 * distribution is compliant with both the GNU General Public License version 2
 * and the aforementioned licenses.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 */
package org.n52.series.api.v1.db.srv;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.n52.io.IoParameters;
import org.n52.io.crs.BoundingBox;
import org.n52.io.crs.CRSUtils;
import org.n52.io.geojson.GeojsonPoint;
import org.n52.io.v1.data.StationOutput;
import org.n52.io.v1.data.TimeseriesOutput;
import org.n52.sensorweb.v1.spi.ParameterService;
import org.n52.series.api.v1.db.da.DataAccessException;
import org.n52.series.api.v1.db.da.DbQuery;
import org.n52.series.api.v1.db.da.StationRepository;
import org.n52.web.InternalServerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StationsAccessService extends ServiceInfoAccess implements ParameterService<StationOutput> {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(StationsAccessService.class);
    
//    private Map<IoParameters, List<StationOutput>> expandedCache;
    
    private List<StationOutput> expandedCache;

    public StationsAccessService(String dbSrid) {
        if (dbSrid != null) {
            StationRepository repository = createStationRepository();
            repository.setDatabaseSrid(dbSrid);
        }
    }
    
    public boolean updateCache() {
        try {
            expandedCache = getExpandedStations(IoParameters.createDefaults());
            return true;
        } catch (DataAccessException e) {
            LOGGER.error("could not update station cache!", e);
            return false;
        }
    }

    @Override
    public StationOutput[] getExpandedParameters(IoParameters query) {
        try {
            if (expandedCache != null/* && expandedCache.containsKey(query) */) {
                List<StationOutput> cachedResults = expandedCache; //expandedCache.get(query);
                List<StationOutput> filteredResults = new ArrayList<>();
                for (StationOutput cachedStation : cachedResults) {
                    // apply possible query filters on each station
                    Object properties = cachedStation.getProperties().get("timeseries");
                    Map<String, TimeseriesOutput> series = (Map<String, TimeseriesOutput>) properties;
                    if (appliesFilter(series, query) && appliesFilter(cachedStation.getGeometry(), query)) {
                        filteredResults.add(cachedStation);
                    }
                }
                return toArray(filteredResults);
            }
            return toArray(getExpandedStations(query));
        }
        catch (DataAccessException e) {
            throw new InternalServerException("Could not get station data.");
        }
    }

    private boolean appliesFilter(Map<String, TimeseriesOutput> series, IoParameters query) {
        for (Entry<String, TimeseriesOutput> entry : series.entrySet()) {
            TimeseriesOutput value = entry.getValue();
            if (query.getServices().contains(value.getService().getId())
                    || query.getCategories().contains(value.getCategory().getId())
                    || query.getProcedures().contains(value.getProcedure().getId())
                    || query.getPhenomena().contains(value.getPhenomenon().getId())
                    || query.getOfferings().contains(value.getOffering().getId())
                    || query.getFeatures().contains(value.getFeature().getId())) {
                return true;
            }
        }
        return false;
    }

    private boolean appliesFilter(GeojsonPoint point, IoParameters query) {
        CRSUtils crsUtils = query.isForceXY()
                ? CRSUtils.createEpsgForcedXYAxisOrder()
                : CRSUtils.createEpsgStrictAxisOrder();
        BoundingBox spatialFilter = query.getSpatialFilter();
        return spatialFilter.contains(crsUtils.convertToPointFrom(point));
    }

    private List<StationOutput> getExpandedStations(IoParameters query) throws DataAccessException {
        DbQuery dbQuery = DbQuery.createFrom(query);
        StationRepository repository = createStationRepository();
        return repository.getAllExpanded(dbQuery);
    }

    @Override
    public StationOutput[] getCondensedParameters(IoParameters query) {
        try {
            DbQuery dbQuery = DbQuery.createFrom(query);
            StationRepository repository = createStationRepository();
            List<StationOutput> results = repository.getAllCondensed(dbQuery);
            return toArray(results);
        }
        catch (DataAccessException e) {
            throw new InternalServerException("Could not get station data.");
        }
    }

    @Override
    public StationOutput[] getParameters(String[] stationsIds) {
        return getParameters(stationsIds, IoParameters.createDefaults());
    }

    @Override
    public StationOutput[] getParameters(String[] stationIds, IoParameters query) {
        try {
            DbQuery dbQuery = DbQuery.createFrom(query);
            StationRepository repository = createStationRepository();
            List<StationOutput> results = new ArrayList<StationOutput>();
            for (String stationId : stationIds) {
                results.add(repository.getInstance(stationId, dbQuery));
            }
            return toArray(results);
        }
        catch (DataAccessException e) {
            throw new InternalServerException("Could not get station data.");
        }
    }

    @Override
    public StationOutput getParameter(String stationsId) {
        return getParameter(stationsId, IoParameters.createDefaults());
    }

    @Override
    public StationOutput getParameter(String stationId, IoParameters query) {
        try {
            DbQuery dbQuery = DbQuery.createFrom(query);
            StationRepository repository = createStationRepository();
            return repository.getInstance(stationId, dbQuery);
        }
        catch (DataAccessException e) {
            throw new InternalServerException("Could not get station data.");
        }
    }

    private StationRepository createStationRepository() {
        return new StationRepository(getServiceInfo());
    }

    private StationOutput[] toArray(List<StationOutput> results) {
        return results.toArray(new StationOutput[0]);
    }

}

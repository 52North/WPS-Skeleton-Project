/**
 * Copyright (C) 2007 - 2015 52°North Initiative for Geospatial Open Source
 * Software GmbH
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 as published
 * by the Free Software Foundation.
 *
 * If the program is linked with libraries which are licensed under one of
 * the following licenses, the combination of the program with the linked
 * library is not considered a "derivative work" of the program:
 *
 *       • Apache License, version 2.0
 *       • Apache Software License, version 1.0
 *       • GNU Lesser General Public License, version 3
 *       • Mozilla Public License, versions 1.0, 1.1 and 2.0
 *       • Common Development and Distribution License (CDDL), version 1.0
 *
 * Therefore the distribution of the program linked with libraries licensed
 * under the aforementioned licenses, is permitted by the copyright holders
 * if the distribution is compliant with both the GNU General Public
 * License version 2 and the aforementioned licenses.
 *
 * As an exception to the terms of the GPL, you may copy, modify,
 * propagate, and distribute a work formed by combining 52°North WPS
 * GeoTools Modules with the Eclipse Libraries, or a work derivative of
 * such a combination, even if such copying, modification, propagation, or
 * distribution would otherwise violate the terms of the GPL. Nothing in
 * this exception exempts you from complying with the GPL in all respects
 * for all of the code used other than the Eclipse Libraries. You may
 * include this exception and its grant of permissions when you distribute
 * 52°North WPS GeoTools Modules. Inclusion of this notice with such a
 * distribution constitutes a grant of such permissions. If you do not wish
 * to grant these permissions, remove this paragraph from your
 * distribution. "52°North WPS GeoTools Modules" means the 52°North WPS
 * modules using GeoTools functionality - software licensed under version 2
 * or any later version of the GPL, or a work based on such software and
 * licensed under the GPL. "Eclipse Libraries" means Eclipse Modeling
 * Framework Project and XML Schema Definition software distributed by the
 * Eclipse Foundation and licensed under the Eclipse Public License Version
 * 1.0 ("EPL"), or a work based on such software and licensed under the EPL.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 */
package testbed12.fo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.xml.namespace.QName;

import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.n52.wps.algorithm.annotation.Algorithm;
import org.n52.wps.algorithm.annotation.AlgorithmMetadata;
import org.n52.wps.algorithm.annotation.ComplexDataInput;
import org.n52.wps.algorithm.annotation.ComplexDataOutput;
import org.n52.wps.algorithm.annotation.Execute;
import org.n52.wps.algorithm.annotation.LiteralDataInput;
import org.n52.wps.algorithm.annotation.ParameterMetadata;
import org.n52.wps.io.GTHelper;
import org.n52.wps.io.SchemaRepository;
import org.n52.wps.io.data.IData;
import org.n52.wps.io.data.binding.complex.GTVectorDataBinding;
import org.n52.wps.io.data.binding.literal.LiteralDoubleBinding;
import org.n52.wps.server.AbstractAnnotatedAlgorithm;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.simplify.DouglasPeuckerSimplifier;
import com.vividsolutions.jts.simplify.TopologyPreservingSimplifier;

@Algorithm(
        version = "1.0.0")
@AlgorithmMetadata(
        roles = { "http://www.opengis.net/spec/wps/2.0/def/process-profile/concept", "http://www.opengis.net/spec/wps/2.0/def/process/description/documentation" },
        hrefs = { "http://52north.github.io/wps-profileregistry/concept/generalization.html", "http://52north.github.io/wps-profileregistry/implementing/douglas-peucker.html" })
public class DouglasPeuckerAlgorithm extends AbstractAnnotatedAlgorithm {
    Logger LOGGER = LoggerFactory.getLogger(DouglasPeuckerAlgorithm.class);

    private List<String> errors = new ArrayList<String>();

    private Double percentage;

    private Boolean preserveTopology;
    
    private FeatureCollection result;

    private FeatureCollection data;

    private double tolerance;

    @ComplexDataOutput(
            identifier = "result", binding = GTVectorDataBinding.class)
    @ParameterMetadata(roles={"http://www.opengis.net/spec/wps/2.0/def/process/description/documentation"}, hrefs={"http://52north.github.io/wps-profileregistry/implementing/douglas-peucker.html#result"})
    public FeatureCollection getResult() {
        return result;
    }

    @ComplexDataInput(
            identifier = "data", binding = GTVectorDataBinding.class)
    @ParameterMetadata(roles={"http://www.opengis.net/spec/wps/2.0/def/process/description/documentation"}, hrefs={"http://52north.github.io/wps-profileregistry/implementing/douglas-peucker.html#data"})
    public void setData(FeatureCollection data) {
        this.data = data;
    }

    @LiteralDataInput(
            identifier = "tolerance")
    @ParameterMetadata(roles={"http://www.opengis.net/spec/wps/2.0/def/process/description/documentation"}, hrefs={"http://52north.github.io/wps-profileregistry/implementing/douglas-peucker.html#tolerance"})
    public void setTolerance(double tolerance) {
        this.tolerance = tolerance;
    }
    
    @LiteralDataInput(
            identifier = "preserve_topology")
    @ParameterMetadata(roles={"http://www.opengis.net/spec/wps/2.0/def/process/description/documentation"}, hrefs={"http://52north.github.io/wps-profileregistry/implementing/douglas-peucker.html#preserve_topology"})
    public void setPreserveTopology(boolean preserveTopology) {
        this.preserveTopology = preserveTopology;
    }

    @Execute
    public void run() {

        double i = 0;
        int totalNumberOfFeatures = data.size();
        String uuid = UUID.randomUUID().toString();
        List<SimpleFeature> featureList = new ArrayList<>();
        SimpleFeatureType featureType = null;
        LOGGER.debug("");
        for (FeatureIterator<?> ia = data.features(); ia.hasNext();) {
            /**
             * ******* How to publish percentage results ************
             */
            i = i + 1;
            percentage = (i / totalNumberOfFeatures) * 100;
            this.update(new Integer(percentage.intValue()));

            /**
             * ******************
             */
            SimpleFeature feature = (SimpleFeature) ia.next();
            Geometry geometry = (Geometry) feature.getDefaultGeometry();
            Geometry geometryBuffered = simplify(geometry, tolerance, preserveTopology);

            if (i == 1) {
                CoordinateReferenceSystem crs = feature.getFeatureType().getCoordinateReferenceSystem();
                if (geometry.getUserData() instanceof CoordinateReferenceSystem) {
                    crs = ((CoordinateReferenceSystem) geometry.getUserData());
                }
                featureType = GTHelper.createFeatureType(feature.getProperties(), geometryBuffered, uuid, crs);
                QName qname = GTHelper.createGML3SchemaForFeatureType(featureType);
                SchemaRepository.registerSchemaLocation(qname.getNamespaceURI(), qname.getLocalPart());

            }

            if (geometryBuffered != null) {
                SimpleFeature createdFeature = (SimpleFeature) GTHelper.createFeature("ID" + new Double(i).intValue(), geometryBuffered, (SimpleFeatureType) featureType, feature.getProperties());
                feature.setDefaultGeometry(geometryBuffered);
                featureList.add(createdFeature);
            } else {
                LOGGER.warn("GeometryCollections are not supported, or result null. Original dataset will be returned");
            }
        }
        result = new ListFeatureCollection(featureList.get(0).getFeatureType(), featureList);

    }

    public List<String> getErrors() {
        return errors;
    }

    private Geometry simplify(Geometry in,
            Double tolerance, boolean preserveTopology) {
        Geometry out = null;

        if(preserveTopology){
            out = TopologyPreservingSimplifier.simplify(in, tolerance);
        }else{
            out = DouglasPeuckerSimplifier.simplify(in, tolerance);
        }
        
        if (in.getGeometryType().equals("MultiPolygon") && out.getGeometryType().equals("Polygon")) {
            MultiPolygon mp = (MultiPolygon) in;
            Polygon[] p = { (Polygon) out };
            return new MultiPolygon(p, mp.getFactory());
        } else if (in.getGeometryType().equals("MultiLineString") && out.getGeometryType().equals("LineString")) {
            MultiLineString ml = (MultiLineString) in;
            LineString[] l = { (LineString) out };
            return new MultiLineString(l, ml.getFactory());
        }

        return out;
    }

}
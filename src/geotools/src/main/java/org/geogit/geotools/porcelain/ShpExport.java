/* Copyright (c) 2013 OpenPlans. All rights reserved.
 * This code is licensed under the BSD New License, available at the root
 * application directory.
 */

package org.geogit.geotools.porcelain;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.geogit.api.GeoGIT;
import org.geogit.api.NodeRef;
import org.geogit.api.ObjectId;
import org.geogit.api.RevFeatureType;
import org.geogit.api.RevObject;
import org.geogit.api.RevObject.TYPE;
import org.geogit.api.RevTree;
import org.geogit.api.plumbing.FindTreeChild;
import org.geogit.api.plumbing.ResolveObjectType;
import org.geogit.api.plumbing.ResolveTreeish;
import org.geogit.api.plumbing.RevObjectParse;
import org.geogit.api.plumbing.RevParse;
import org.geogit.cli.CLICommand;
import org.geogit.cli.CommandFailedException;
import org.geogit.cli.GeogitCLI;
import org.geogit.cli.RequiresRepository;
import org.geogit.geotools.plumbing.ExportOp;
import org.geogit.geotools.plumbing.GeoToolsOpException;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.opengis.feature.simple.SimpleFeatureType;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Optional;

/**
 * Exports features from a feature type into a shapefile.
 * 
 * @see ExportOp
 */
@RequiresRepository
@Parameters(commandNames = "export", commandDescription = "Export to Shapefile")
public class ShpExport extends AbstractShpCommand implements CLICommand {

    @Parameter(description = "<path> <shapefile>", arity = 2)
    public List<String> args;

    @Parameter(names = { "--overwrite", "-o" }, description = "Overwrite output file")
    public boolean overwrite;

    @Parameter(names = { "--defaulttype" }, description = "Export only features with the tree default feature type if several types are found")
    public boolean defaultType;

    @Parameter(names = { "--alter" }, description = "Export all features if several types are found, altering them to adapt to the output feature type")
    public boolean alter;

    @Parameter(names = { "--featuretype" }, description = "Export only features with the specified feature type if several types are found")
    @Nullable
    public String sFeatureTypeId;

    /**
     * Executes the export command using the provided options.
     */
    @Override
    protected void runInternal(GeogitCLI cli) throws IOException {
        if (args.isEmpty()) {
            printUsage();
            throw new CommandFailedException();
        }

        String path = args.get(0);
        String shapefile = args.get(1);

        ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();

        File file = new File(shapefile);
        if (file.exists() && !overwrite) {
            throw new CommandFailedException(
                    "The selected shapefile already exists. Use -o to overwrite");
        }

        Map<String, Serializable> params = new HashMap<String, Serializable>();
        params.put(ShapefileDataStoreFactory.URLP.key, new File(shapefile).toURI().toURL());
        params.put(ShapefileDataStoreFactory.CREATE_SPATIAL_INDEX.key, Boolean.TRUE);

        ShapefileDataStore dataStore = (ShapefileDataStore) dataStoreFactory
                .createNewDataStore(params);

        SimpleFeatureType outputFeatureType;
        ObjectId featureTypeId;
        if (sFeatureTypeId != null) {
            // Check the feature type id string is a correct id
            Optional<ObjectId> id = cli.getGeogit().command(RevParse.class)
                    .setRefSpec(sFeatureTypeId).call();
            checkParameter(id.isPresent(), "Invalid feature type reference", sFeatureTypeId);
            TYPE type = cli.getGeogit().command(ResolveObjectType.class).setObjectId(id.get())
                    .call();
            checkParameter(type.equals(TYPE.FEATURETYPE),
                    "Provided reference does not resolve to a feature type: ", sFeatureTypeId);
            outputFeatureType = (SimpleFeatureType) cli.getGeogit().command(RevObjectParse.class)
                    .setObjectId(id.get()).call(RevFeatureType.class).get().type();
            featureTypeId = id.get();
        } else {
            try {
                outputFeatureType = getFeatureType(path, cli);
                featureTypeId = null;
            } catch (GeoToolsOpException e) {
                cli.getConsole().println("No features to export.");
                return;
            }
        }
        dataStore.createSchema(outputFeatureType);

        final String typeName = dataStore.getTypeNames()[0];
        final SimpleFeatureSource featureSource = dataStore.getFeatureSource(typeName);
        if (!(featureSource instanceof SimpleFeatureStore)) {
            throw new CommandFailedException("Could not create feature store.");
        }
        final SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;
        ExportOp op = cli.getGeogit().command(ExportOp.class).setFeatureStore(featureStore)
                .setPath(path).setFeatureTypeId(featureTypeId).setAlter(alter);
        if (defaultType) {
            op.exportDefaultFeatureType();
        }
        try {
            op.setProgressListener(cli.getProgressListener()).call();
        } catch (IllegalArgumentException iae) {
            throw new org.geogit.cli.InvalidParameterException(iae.getMessage(), iae);
        } catch (GeoToolsOpException e) {
            file.delete();
            switch (e.statusCode) {
            case MIXED_FEATURE_TYPES:
                throw new CommandFailedException(
                        "Error: The selected tree contains mixed feature types. Use --defaulttype or --featuretype <feature_type_ref> to export.",
                        e);
            default:
                throw new CommandFailedException("Could not export. Error:" + e.statusCode.name(),
                        e);
            }
        }
        cli.getConsole().println(path + " exported successfully to " + shapefile);

    }

    private SimpleFeatureType getFeatureType(String path, GeogitCLI cli) {

        checkParameter(path != null, "No path specified.");

        String refspec;
        if (path.contains(":")) {
            refspec = path;
        } else {
            refspec = "WORK_HEAD:" + path;
        }

        checkParameter(!refspec.endsWith(":"), "No path specified.");

        final GeoGIT geogit = cli.getGeogit();

        Optional<ObjectId> rootTreeId = geogit.command(ResolveTreeish.class)
                .setTreeish(refspec.split(":")[0]).call();

        checkParameter(rootTreeId.isPresent(), "Couldn't resolve '" + refspec
                + "' to a treeish object");

        RevTree rootTree = geogit.getRepository().getTree(rootTreeId.get());
        Optional<NodeRef> featureTypeTree = geogit.command(FindTreeChild.class)
                .setChildPath(refspec.split(":")[1]).setParent(rootTree).setIndex(true).call();

        checkParameter(featureTypeTree.isPresent(), "pathspec '" + refspec.split(":")[1]
                + "' did not match any valid path");

        Optional<RevObject> revObject = cli.getGeogit().command(RevObjectParse.class)
                .setObjectId(featureTypeTree.get().getMetadataId()).call();
        if (revObject.isPresent() && revObject.get() instanceof RevFeatureType) {
            RevFeatureType revFeatureType = (RevFeatureType) revObject.get();
            if (revFeatureType.type() instanceof SimpleFeatureType) {
                return (SimpleFeatureType) revFeatureType.type();
            } else {
                throw new IllegalArgumentException(
                        "Cannot find feature type for the specified path");
            }
        } else {
            throw new IllegalArgumentException("Cannot find feature type for the specified path");
        }

    }
}

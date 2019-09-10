package com.jug.fijiplugins;

import java.io.File;

import com.jug.gurobi.GurobiInstaller;
import com.jug.util.FloatTypeImgLoader;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.plugin.HyperStackConverter;
import ij.plugin.PlugIn;

/**
 * This plugin represents the full pipeline of the MoMA analysis
 * - Registration of images (motion correction)
 * - Splitting the different growth channels from the original huge images
 * - Analyse one particular growth channel. If the user wants to analyse another Growth channel, he just need to restart this plugin.
 *
 * Author: Robert Haase, Scientific Computing Facility, MPI-CBG Dresden, rhaase@mpi-cbg.de
 * Date: October 2016
 */
class MotherMachineDefaultPipelinePlugin implements PlugIn {

    private static String currentDir = Prefs.getDefaultDirectory();

    public MotherMachineDefaultPipelinePlugin()
    {
        //currentDir = Prefs.getDefaultDirectory();
        //IJ.log("current dir: " + currentDir);

        IJ.register(this.getClass());
    }


    @Override
    public void run(final String s) {

        if(!GurobiInstaller.checkInstallation()) {
            IJ.log("Gurobi appears not properly installed. Please check your installation!");
            return;
        }

        // -------------------------------------------------------------------------------
        // plugin configuration
        final GenericDialogPlus gd = new GenericDialogPlus("MoMA configuration");
        if (s.equals("file")) {
            gd.addFileField("Input_file", currentDir);
        } else {
            gd.addDirectoryField("Input_folder", currentDir);
        }
        //gd.addNumericField("Number of Channels", 2, 0);


        gd.addMessage("Advanced splitting parameters (MMPreprocess)");
        gd.addNumericField("Variance threshold", 0.001, 8);
        gd.addNumericField("Lateral offset", 40, 0);
        gd.addNumericField("Crop width", 100, 0);

        gd.showDialog();
        if (gd.wasCanceled()) {
            return;
        }
        String inputFolder = gd.getNextString();

        if (!s.equals("file")) {
            if (!inputFolder.endsWith("/")) {
                inputFolder = inputFolder + "/";
            }
        }
        //int numberOfChannels = (int) gd.getNextNumber();


        final double varianceThreshold = gd.getNextNumber();
        final int lateralOffset = (int)gd.getNextNumber();
        final int cropWidth = (int)gd.getNextNumber();

        currentDir = inputFolder;

        final File inputFolderFile = new File(inputFolder);
        if (!inputFolderFile.exists()) {
            IJ.log("The input folder(" + inputFolder + ") does not exist. Aborting...");
            return;
        }
        String dataSetName;
        if (inputFolderFile.isDirectory()) {
            dataSetName = inputFolderFile.getName();
        }
        else
        {
            dataSetName = inputFolderFile.getName().replace(".tiff","").replace(".tif","");
        }

        // -------------------------------------------------------------------------------
        // create subfolders for intermediate and final analysis results
        String registeredFolder = inputFolder + "1_registered/";
        String splitFolder = inputFolder + "2_split/";
        String analysisResultsFolder = inputFolder + "3_analysed/";

        if (s.equals("file")) {
            registeredFolder = inputFolder + "_1_registered/";
            splitFolder = inputFolder + "_2_split/";
            analysisResultsFolder = inputFolder + "_3_analysed/";
        }

        Utilities.ensureFolderExists(registeredFolder);
        Utilities.ensureFolderExists(splitFolder);
        Utilities.ensureFolderExists(analysisResultsFolder);

        boolean executeRegistration = true;
        boolean executeSplitting = true;

        if (Utilities.countFilesInFolder(registeredFolder) > 0) {
            executeRegistration = false;
        }

        if (Utilities.countFilesInFolder(splitFolder) > 0) {
            executeSplitting = false;
        }

        int numberOfTimePoints;
        int numberOfChannels = 0;
        if (executeRegistration) {

            ImagePlus imp;
            ImagePlus hyperStackImp;

            // -------------------------------------------------------------------------------
            // Importing
            if (new File(inputFolder).isDirectory()) {
                IJ.run("Image Sequence...", "open=" + inputFolder + " sort");

                int min_c = Integer.MAX_VALUE;
                int max_c = Integer.MIN_VALUE;

                for (final File image : inputFolderFile.listFiles(FloatTypeImgLoader.tifFilter)) {
                    final int c = FloatTypeImgLoader.getChannelFromFilename(image.getName());
                    if (c < min_c) {
                        min_c = c;
                    }
                    if (c > max_c) {
                        max_c = c;
                    }
                }

                numberOfChannels = max_c - min_c + 1;


                imp = IJ.getImage();
                final int numberOfSlices = imp.getNSlices();
                numberOfTimePoints = numberOfSlices / numberOfChannels;

                hyperStackImp = HyperStackConverter.toHyperStack(imp, numberOfChannels, 1, numberOfTimePoints, "default", "Color");
                hyperStackImp.show();

                // -------------------------------------------------------------------------------
                // Registration
                IJ.run(hyperStackImp, "HyperStackReg", "transformation=[Rigid Body] sliding");
            } else {
                // if it's a file:
                imp = IJ.openImage(inputFolder);
                numberOfChannels = imp.getNChannels();
                numberOfTimePoints = imp.getNFrames();
                imp.show();
                hyperStackImp = imp;
            }

            final ImagePlus registeredStackImp = IJ.getImage();

            // -------------------------------------------------------------------------------
            // Save intermediate results
            //IJ.run(registeredStackImp, "Image Sequence... ", "format=TIFF digits=4 save=[" + registeredFolder + "]");
            IJ.saveAsTiff(registeredStackImp, registeredFolder + dataSetName + ".tif");

            // cleanup
            registeredStackImp.close();
            hyperStackImp.close();
            if (hyperStackImp != imp ) {
                imp.close();
            }
        } else {
            IJ.log("Skipping registration...");
            //numberOfTimePoints = Utilities.countFilesInFolder(registeredFolder) / numberOfChannels;

            final File registeredFolderFile = new File(registeredFolder);
            final File[] filelist = registeredFolderFile.listFiles(FloatTypeImgLoader.tifFilter);
            if (filelist.length == 1) { // registration result saved as single stack file
                final ImagePlus imp = IJ.openImage(filelist[0].getAbsolutePath());
                numberOfChannels = imp.getNChannels();
                numberOfTimePoints = imp.getNFrames();
            } else {
                int min_t = Integer.MAX_VALUE;
                int max_t = Integer.MIN_VALUE;
                for (final File image : filelist) {

                    final int t = FloatTypeImgLoader.getChannelFromFilename(image.getName());
                    if (t < min_t) {
                        min_t = t;
                    }
                    if (t > max_t) {
                        max_t = t;
                    }
                }
                numberOfTimePoints = max_t - min_t + 1;
            }
        }

        if (executeSplitting) {
            // -------------------------------------------------------------------------------
            // Run MMPreprocess

            final String parameters =
                    "input_file=[" + registeredFolder + dataSetName + ".tif" + "]" +
                            " output_folder=[" + splitFolder + "]" +
                            " number_of_Time_points=" + numberOfTimePoints +
                            " time_points_start_with=1" +
                            " auto_rotation" +
                            " variance_threshold=" + varianceThreshold +
                            " gl_min_length=250" +
                            " row_smoothing_sigma=20" +
                            " lateral_offset=" + lateralOffset +
                            " fake_GL_width=20" +
                            " crop_width=" + cropWidth +
                            " top_padding=20" +
                            " bottom_padding=20";

			System.out.println( "Starting single file preprocessing with: " + parameters );

            IJ.run("MoMA pre-processing a single file", parameters);
        } else {
            IJ.log("Skipping splitting (MMPreprocess)...");
        }

        // -------------------------------------------------------------------------------
        // Dataset selection

        final String[] datasets = Utilities.listSubFolderNames(splitFolder);

        if (datasets.length == 0) {
            IJ.log("No data sets found. Consider removing the 2_split subfolder to rerun splitting (MMPreprocess).");
            return;
        }
        final String[] dataSetDescriptions = new String[datasets.length];
        int nextIndexToAnalyse = -1;
        for (int i = 0; i < datasets.length; i++) {
            dataSetDescriptions[i] = datasets[i];
            if (Utilities.countFilesInFolder(analysisResultsFolder + datasets[i] + "/") > 0) {
                dataSetDescriptions[i] += " *";
            } else {
                if (nextIndexToAnalyse < 0)
                {
                    nextIndexToAnalyse = i;
                }
            }
        }
        String selectedDataset = "";
        if (nextIndexToAnalyse >= 0)
        {
            selectedDataset = dataSetDescriptions[nextIndexToAnalyse];
        }

        final GenericDialogPlus gdDataSetSelection = new GenericDialogPlus("MoMA dataset selection");
        gdDataSetSelection.addChoice("Dataset", dataSetDescriptions, selectedDataset);
        gdDataSetSelection.addMessage("Datasets marked with a * were analysed already.");
        gdDataSetSelection.showDialog();
        if (gdDataSetSelection.wasCanceled()) {
            return;
        }
        final String selectedDataSet = datasets[gdDataSetSelection.getNextChoiceIndex()];

        final String momaInputFolder = splitFolder + selectedDataSet + "/";
        final String momaOutputFolder = analysisResultsFolder + selectedDataSet + "/";

        // -------------------------------------------------------------------------------
        // create MoMA output folder; it would exit if it not exists
        Utilities.ensureFolderExists(momaOutputFolder);

        // -------------------------------------------------------------------------------
        // Running actual MoMA
        final String momaParameters =
                "input_folder=[" + momaInputFolder + "]" +
                " output_folder=[" + momaOutputFolder + "]" +
                " number_of_channels=" + numberOfChannels;

        IJ.log("MoMA params: " + momaParameters);

        IJ.run("MoMA", momaParameters);


        // -------------------------------------------------------------------------------
    }


}

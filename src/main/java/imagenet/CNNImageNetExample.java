package imagenet;


import imagenet.Utils.ImageNetDataSetIterator;
import imagenet.Utils.ImageNetLoader;
import imagenet.Utils.ModelUtils;
import imagenet.sampleModels.LeNet;
import imagenet.sampleModels.VGGNetA;
import imagenet.sampleModels.VGGNetD;
import org.apache.commons.io.FilenameUtils;
import org.deeplearning4j.datasets.iterator.DataSetIterator;
import org.deeplearning4j.datasets.iterator.MultipleEpochsIterator;
import org.deeplearning4j.eval.Evaluation;
import org.deeplearning4j.gradientcheck.GradientCheckUtil;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.optimize.api.IterationListener;
import org.deeplearning4j.optimize.listeners.ParamAndGradientIterationListener;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import imagenet.sampleModels.AlexNet;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

import static org.junit.Assert.assertTrue;

/**
 *
 * Olga Russakovsky*, Jia Deng*, Hao Su, Jonathan Krause, Sanjeev Satheesh, Sean Ma, Zhiheng Huang,
 * Andrej Karpathy, Aditya Khosla, Michael Bernstein, Alexander C. Berg and Li Fei-Fei.
 * (* = equal contribution) ImageNet Large Scale Visual Recognition Challenge. arXiv:1409.0575, 2014.
 *

 * Created by nyghtowl on 9/24/15.
 */
public class CNNImageNetExample {

    private static final Logger log = LoggerFactory.getLogger(CNNImageNetExample.class);

    // values to pass in from command line when compiled, esp running remotely
    @Option(name="--modelType",usage="Type of model (AlexNet, VGGNetA, VGGNetB)",aliases = "-mT")
    private String modelType = "AlexNet";
    @Option(name="--batchSize",usage="Batch size",aliases="-b")
    private int batchSize = 10;
    @Option(name="--testBatchSize",usage="Test Batch size",aliases="-tB")
    private int testBatchSize = 10;
    @Option(name="--numBatches",usage="Number of batches",aliases="-nB")
    private int numBatches = 1;
    @Option(name="--numTestBatches",usage="Number of test batches",aliases="-nTB")
    private int numTestBatches = 1;
    @Option(name="--numEpochs",usage="Number of epochs",aliases="-nE")
    private int numEpochs = 1;
    @Option(name="--iterations",usage="Number of iterations",aliases="-i")
    private int iterations = 2;
    @Option(name="--numCategories",usage="Number of categories",aliases="-nC")
    private int numCategories = 4;
    @Option(name="--trainFolder",usage="Train folder",aliases="-taF")
    private String trainFolder = "train";
    @Option(name="--testFolder",usage="Test folder",aliases="-teF")
    private String testFolder = "test";
    @Option(name="--saveModel",usage="Save model",aliases="-sM")
    private boolean saveModel = false;
    @Option(name="--saveParams",usage="Save parameters",aliases="-sP")
    private boolean saveParams = false;
    @Option(name="--confName",usage="Model configuration file name",aliases="-conf")
    private String confName = null;
    @Option(name="--paramName",usage="Parameter file name",aliases="-param")
    private String paramName = null;

    public CNNImageNetExample() {
    }

    public void doMain(String[] args) throws Exception {
        String outputPath = ModelUtils.defineOutputDir(modelType.toString());

        // Parse command line arguments if they exist
        CmdLineParser parser = new CmdLineParser(this);
        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            // handling of wrong arguments
            System.err.println(e.getMessage());
            parser.printUsage(System.err);
        }

        boolean train = true;
        boolean splitTrainData = false;
        boolean gradientCheck = false;
        boolean loadModel = false;
        boolean loadParams = false;

        MultiLayerNetwork model = null;
        DataSetIterator dataIter, testIter;
        long startTimeTrain = 0;
        long endTimeTrain = 0;
        long startTimeEval = 0;
        long endTimeEval = 0;

        // libraries like Caffe scale to 256?
        final int numRows = 224;
        final int numColumns = 224;
        int nChannels = 3;
        int outputNum = 1860;
        int seed = 123;
        int listenerFreq = 1;
//        int[] layerIdsA = {0,1,3,4,13,14,15}; // specific to VGGA
//        int[] layerIdsD = {0,1,3,4,18,19,20}; // specific to VGGD
        String[] layerIdsVGG = {"cnn1", "cnn2", "cnn3", "cnn4", "ffn1", "ffn2", "output"};
        Map<String, String> paramPaths = null;

        int totalTrainNumExamples = batchSize * numBatches;
        int totalTestNumExamples = batchSize * numTestBatches;
        //        String basePath = FilenameUtils.concat(System.getProperty("user.home"), "Documents/skymind/imagenet");
        String basePath = ImageNetLoader.BASE_DIR;
        String trainData = FilenameUtils.concat(basePath, trainFolder);
        String testData = FilenameUtils.concat(basePath, testFolder);
        String labelPath = FilenameUtils.concat(basePath, ImageNetLoader.LABEL_FILENAME);
        String valLabelMap = FilenameUtils.concat(basePath, ImageNetLoader.VAL_MAP_FILENAME);

        log.info("Load data....");
        dataIter = new ImageNetDataSetIterator(batchSize, totalTrainNumExamples, new int[] {numRows, numColumns, nChannels}, numCategories, outputNum);

//        DataSet ds = dataIter.next();
        log.info("Build model....");
        if (confName != null && paramName != null) {
            String confPath = FilenameUtils.concat(outputPath, confName + "conf.yaml");
            String paramPath = FilenameUtils.concat(outputPath, paramName + "param.bin");
            model = ModelUtils.loadModelAndParameters(new File(confPath), paramPath);
        } else {
            switch (modelType) {
                case "LeNet":
                    model = new LeNet(numRows, numColumns, nChannels, outputNum, seed, iterations).init();
                    break;
                case "AlexNet":
                    model = new AlexNet(numRows, numColumns, nChannels, outputNum, seed, iterations).init();
                    break;
                case "VGGNetA":
                    model = new VGGNetA(numRows, numColumns, nChannels, outputNum, seed, iterations).init();
                    break;
                case "VGGNetD":
                    model = new VGGNetD(numRows, numColumns, nChannels, outputNum, seed, iterations).init();
                    if (loadParams) {
                        paramPaths = ModelUtils.getStringParamPaths(model, outputPath, layerIdsVGG);
                        ModelUtils.loadParameters(model, layerIdsVGG, paramPaths);
                    }
                    break;
            }
        }
        ;
        // Listeners
        IterationListener paramListener = ParamAndGradientIterationListener.builder()
                .outputToFile(true)
                .file(new File(System.getProperty("java.io.tmpdir") + "/paramAndGradTest.txt"))
                .outputToConsole(true).outputToLogger(false)
                .iterations(1).printHeader(true)
                .printMean(false)
                .printMinMax(false)
                .printMeanAbsValue(true)
                .delimiter("\t").build();

        model.setListeners(Arrays.asList((IterationListener) new ScoreIterationListener(listenerFreq)));
//        model.setListeners(Arrays.asList((IterationListener) new HistogramIterationListener(listenerFreq)));
//        model.setListeners(Arrays.asList(new ScoreIterationListener(listenerFreq), paramListener));

        if (gradientCheck) gradientCheck(dataIter, model);

        if (train) {
            log.info("Train model....");

            //TODO need dataIter that loops through set number of examples like SamplingIter but takes iter vs dataset
            MultipleEpochsIterator epochIter = new MultipleEpochsIterator(numEpochs, dataIter);
////                asyncIter = new AsyncDataSetIterator(dataIter, 1); TODO doesn't have next(num)
            Evaluation eval = new Evaluation(dataIter.getLabels());

            // split training and evaluatioin out of same DataSetIterator
            if (splitTrainData) {
                int splitTrainNum = (int) (batchSize * .8);
                int numTestExamples = totalTrainNumExamples / numBatches - splitTrainNum;

                for (int i = 0; i < numEpochs; i++) {
                    for (int j = 0; j < numBatches; j++)
                        model.fit(epochIter.next(splitTrainNum)); // if spliting test train in same dataset - put size of train in next
                    eval = evaluatePerformance(model, epochIter, numTestExamples, eval);
                }
            } else {
                startTimeTrain = System.currentTimeMillis();
                for (int i = 0; i < numEpochs; i++) {
                    for (int j = 0; j < numBatches; j++)
                        model.fit(epochIter.next());
                }
                endTimeTrain = System.currentTimeMillis();

                // TODO incorporate code when using full validation set to pass valLabelMap through iterator
//                RecordReader testRecordReader = new ImageNetRecordReader(numColumns, numRows, nChannels, true, labelPath, valLabelMap); // use when pulling from main val for all labels
//                testRecordReader.initialize(new LimitFileSplit(new File(testData), allForms, totalNumExamples, numCategories, Pattern.quote("_"), 0, new Random(123)));
//                testIter = new RecordReaderDataSetIterator(testRecordReader, batchSize, numRows * numColumns * nChannels, 1860);

                testIter = new ImageNetDataSetIterator(testBatchSize, totalTestNumExamples, new int[] {numRows, numColumns, nChannels}, numCategories, outputNum, "CLS_VAL");
                MultipleEpochsIterator testEpochIter = new MultipleEpochsIterator(numEpochs, testIter);

                startTimeEval = System.currentTimeMillis();
                eval = evaluatePerformance(model, testEpochIter, testBatchSize, eval);
                endTimeEval = System.currentTimeMillis();
            }
            log.info(eval.stats());

            System.out.println("Total training runtime: " + ((endTimeTrain-startTimeTrain)/60000) + " minutes");
            System.out.println("Total evaluation runtime: " + ((endTimeEval - startTimeEval) / 60000) + " minutes");
            log.info("****************************************************");

            if (saveModel) ModelUtils.saveModelAndParameters(model, outputPath.toString());
            if (saveParams) ModelUtils.saveParameters(model, layerIdsVGG, paramPaths);

            log.info("****************Example finished********************");
        }
    }


    private Evaluation evaluatePerformance(MultiLayerNetwork model, MultipleEpochsIterator iter, int testBatchSize, Evaluation eval){
        log.info("Evaluate model....");
        DataSet imgNet;
        INDArray output;

        //TODO setup iterator to randomize
        for(int i=0; i < numTestBatches; i++){
            imgNet = iter.next(testBatchSize);
            output = model.output(imgNet.getFeatureMatrix());
            eval.eval(imgNet.getLabels(), output);
        }
        return eval;
    }

    private void gradientCheck(DataSetIterator dataIter, MultiLayerNetwork model){
        DataSet imgNet;
        log.info("Gradient Check....");

        imgNet = dataIter.next();
        String name = new Object() {
        }.getClass().getEnclosingMethod().getName();

        model.setInput(imgNet.getFeatures());
        model.setLabels(imgNet.getLabels());
        model.computeGradientAndScore();
        double scoreBefore = model.score();
        for (int j = 0; j < 1; j++)
            model.fit(imgNet);
        model.computeGradientAndScore();
        double scoreAfter = model.score();
//            String msg = name + " - score did not (sufficiently) decrease during learning (before=" + scoreBefore + ", scoreAfter=" + scoreAfter + ")";
//            assertTrue(msg, scoreAfter < 0.8 * scoreBefore);
        for (int j = 0; j < model.getnLayers(); j++)
            System.out.println("Layer " + j + " # params: " + model.getLayer(j).numParams());

        double default_eps = 1e-6;
        double default_max_rel_error = 0.25;
        boolean print_results = true;
        boolean return_on_first_failure = false;
        boolean useUpdater = true;

        boolean gradOK = GradientCheckUtil.checkGradients(model, default_eps, default_max_rel_error,
                print_results, return_on_first_failure, imgNet.getFeatures(), imgNet.getLabels(), useUpdater);

        assertTrue(gradOK);

    }

    public static void main(String[] args) throws Exception {
        new CNNImageNetExample().doMain(args);
    }


}

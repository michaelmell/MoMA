package com.jug;

import org.junit.Test;
import scala.Int;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;

public class MoMATest {
    public static void main(String[] args){
        MoMATest tests = new MoMATest();
        // TODO-MM-20191120: User tmin and tmax instead of having multiple duplicated datasets, with different frame-ranges.
        tests._lysing_cell__thomas_20200922__Pos0_GL30();
//        tests._lysing_cell__thomas_20200922__Pos16_GL17();
//        tests._lis_20210303__Pos0_GL14();
//        tests._theo__20210126_glc_spcm_1__Pos1_GL15();
//        tests._theo__20210126_glc_spcm_1__Pos1_GL14();
//        tests._20190515_hi1_med1_med2_rpmB_glu_gly_7_chr_rpmB_curated__dataset__Pos25_GL1();
//        tests._20200516_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_gluaa_glyaa_2_MMStack_Pos3_preproc_GL02(); // problematic dataset
//        tests._20200516_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_gluaa_glyaa_2_MMStack_Pos3_preproc_GL06(); // problematic dataset
//        tests._20191112_glc_spcm_1_MMStack_Pos7_preproc_GL15();
//        tests._20200528_glcIPTG_spcm_1_MMStack_Pos15_preproc_GL01(); // problematic dataset
//        tests._20180711_glyc_lactuloseTMG20uM_1_MMStack_Pos1_preproc_GL07();
//        tests._20180711_glyc_lactuloseTMG20uM_1_MMStack_Pos3_preproc_GL16();
//        tests._lugange_001();
//        tests._20190515_hi1_med1_med2_rpmB_glu_gly_7_MMStack_Pos0_preproc_GL04();
//        tests._20200417_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_richdefinedmedium_8_MMStack_Pos14_preproc_GL14(); // dataset with inhomogeneous PhC (rich-media cells)
//        tests._cropped_420200403_5_rpmB_1_rrnB_synthrich7_1_MMStack_Pos13_preproc_GL16();
//        tests._20190614_glu_lowLac_1_MMStack_Pos0_preproc_GL03();
//        tests._20190614_glu_lowLac_1_MMStack_Pos0_preproc_GL02();  // dataset with jumping cell
//        tests._new_10frames_20190424_hi2_hi3_med2_rplN_4_MMStack_Pos0_GL3(); // Bad segmentation with model_20200605-134801_36cfc364_cb8fe485.zip
//        tests._50frames_20190424_hi2_hi3_med2_rplN_4_MMStack_Pos0_GL3(); // Bad segmentation with model_20200605-134801_36cfc364_cb8fe485.zip
//        tests._450frames_20190424_hi2_hi3_med2_rplN_4_MMStack_Pos0_GL3(); // Bad segmentation with model_20200605-134801_36cfc364_cb8fe485.zip
//        tests._20190424_hi2_hi3_med2_rplN_4_MMStack_Pos0_GL3(); // Bad segmentation with model_20200605-134801_36cfc364_cb8fe485.zip
//        tests._cropped_20190424_hi2_hi3_med2_rplN_4_MMStack_Pos0_GL3(); // Bad segmentation with model_20200605-134801_36cfc364_cb8fe485.zip
//        tests._20190515_hi1_med1_med2_rpmB_glu_gly_7_MMStack_Pos25_preproc_GL01__frames_400_450();
//        tests.headless_20190515_hi1_med1_med2_rpmB_glu_gly_7_MMStack_Pos25_preproc_GL01__frames_400_450();
//        tests._20190515_hi1_med1_med2_rpmB_glu_gly_7_MMStack_Pos25_preproc_GL01();
//        tests._20191105_glc_spcm_1_MMStack_Pos7_preproc_GL15();
//        tests._cropped_20191105_glc_spcm_1_MMStack_Pos7_preproc_GL15();
    }

//-i
///home/micha/Documents/01_work/git/MoMA/test_datasets/Dany_synthetic_rich_media/cropped_420200403_5_rpmB_1_rrnB_synthrich7_1_MMStack_Pos13_preproc_GL16.tif
//##########################################################
//        -i
///home/micha/Documents/01_work/git/MoMA/test_datasets/Dany_synthetic_rich_media/420200403_5_rpmB_1_rrnB_synthrich7_1_MMStack_Pos13_preproc_GL16.tif


    @Test
    public void _lysing_cell__thomas_20200922__Pos0_GL30() {
        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/000_moma_benchmarking/problem_cases/lysing_cell_inside_gl/thomas_20200922/Pos0_GL30/cropped__20200922_M9glc_VNG1040-hi2_AB_1_MMStack_Pos0_GL30.tiff";
        String outputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/000_moma_benchmarking/problem_cases/lysing_cell_inside_gl/thomas_20200922/Pos0_GL30/output/";
        Integer tmin = 120;
        Integer tmax = 140;
        startMoma(inputPath, outputPath, tmin, tmax);
//        startMoma(inputPath, outputPath);
    }

    @Test
    public void _lysing_cell__thomas_20200922__Pos16_GL17() {
        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/000_moma_benchmarking/problem_cases/lysing_cell_inside_gl/thomas_20200922/Pos16_GL17/cropped__20200922_M9glc_VNG1040-hi2_AB_1_MMStack_Pos16_GL17.tiff";
        String outputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/000_moma_benchmarking/problem_cases/lysing_cell_inside_gl/thomas_20200922/Pos16_GL17/output/";
        Integer tmin = 110;
        Integer tmax = 140;
        startMoma(inputPath, outputPath, tmin, tmax);
    }

    @Test
    public void _lis_20210303__Pos0_GL14() {
//        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/lis_20210303/Pos0_GL14/20210303_VNG40_12h_2hAB_10h_1_MMStack_Pos0_GL14.tiff";
        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/lis_20210303/Pos0_GL14/20210303_VNG40_12h_2hAB_10h_1_MMStack_Pos0_GL14__cropped.tiff";
        String outputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/lis_20210303/Pos0_GL14/output/";
        Integer tmin = 100;
        Integer tmax = 200;
//        startMoma(inputPath, outputPath);
        startMoma(inputPath, outputPath, tmin, tmax);
    }

    @Test
    public void _theo__20210126_glc_spcm_1__Pos1_GL15() {
        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20200626__datasets_for_work_on_ticket_021-improve-image-normalization/00_datasets_from_theo/01_bad_datasets/theo__20210126_glc_spcm_1/Pos1_GL15/20210126_glc_spcm_1_MMStack_Pos1_GL15.tiff";
        String outputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20200626__datasets_for_work_on_ticket_021-improve-image-normalization/00_datasets_from_theo/01_bad_datasets/theo__20210126_glc_spcm_1/Pos1_GL15/output/";
        startMoma(inputPath, outputPath);
    }

    @Test
    public void _theo__20210126_glc_spcm_1__Pos1_GL14() {
        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20200626__datasets_for_work_on_ticket_021-improve-image-normalization/00_datasets_from_theo/01_bad_datasets/theo__20210126_glc_spcm_1/Pos1_GL14/20210126_glc_spcm_1_MMStack_Pos1_GL14.tiff";
        String outputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20200626__datasets_for_work_on_ticket_021-improve-image-normalization/00_datasets_from_theo/01_bad_datasets/theo__20210126_glc_spcm_1/Pos1_GL14/output/";
        startMoma(inputPath, outputPath);
    }

    @Test
    public void _20200516_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_gluaa_glyaa_2_MMStack_Pos3_preproc_GL06() {
        // NOTES: * cropped image performs slightly better, but not good
//        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20200626__datasets_for_work_on_ticket_021-improve-image-normalization/01_dany_datasets/20200516_bottom_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_gluaa_glyaa/20200516_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_gluaa_glyaa_2_MMStack_Pos3_preproc_GL06/20200516_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_gluaa_glyaa_2_MMStack_Pos3_preproc_GL06.tif";
        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20200626__datasets_for_work_on_ticket_021-improve-image-normalization/01_dany_datasets/20200516_bottom_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_gluaa_glyaa/20200516_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_gluaa_glyaa_2_MMStack_Pos3_preproc_GL06/cropped_20200516_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_gluaa_glyaa_2_MMStack_Pos3_preproc_GL06.tif";
//        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20200626__datasets_for_work_on_ticket_021-improve-image-normalization/01_dany_datasets/20200516_bottom_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_gluaa_glyaa/20200516_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_gluaa_glyaa_2_MMStack_Pos3_preproc_GL06/20200629__frame_sets_for_comparison/frame_346_bad__cropped_20200516_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_gluaa_glyaa_2_MMStack_Pos3_preproc_GL06.tif";
//        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20200626__datasets_for_work_on_ticket_021-improve-image-normalization/01_dany_datasets/20200516_bottom_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_gluaa_glyaa/20200516_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_gluaa_glyaa_2_MMStack_Pos3_preproc_GL06/20200629__frame_sets_for_comparison/frame_346_bad__PhC__16bit__cropped_20200516_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_gluaa_glyaa_2_MMStack_Pos3_preproc_GL06.tif";
//        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20200626__datasets_for_work_on_ticket_021-improve-image-normalization/01_dany_datasets/20200516_bottom_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_gluaa_glyaa/20200516_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_gluaa_glyaa_2_MMStack_Pos3_preproc_GL06/20200629__frame_sets_for_comparison/frame_346_bad__PhC__16bit__MATCHED_HISTOGRAM__cropped_20200516_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_gluaa_glyaa_2_MMStack_Pos3_preproc_GL06.tif";
//        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20200626__datasets_for_work_on_ticket_021-improve-image-normalization/01_dany_datasets/20200516_bottom_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_gluaa_glyaa/20200516_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_gluaa_glyaa_2_MMStack_Pos3_preproc_GL06/20200629__frame_sets_for_comparison/frame_345_good__cropped_20200516_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_gluaa_glyaa_2_MMStack_Pos3_preproc_GL06.tif";
//        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20200626__datasets_for_work_on_ticket_021-improve-image-normalization/01_dany_datasets/20200516_bottom_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_gluaa_glyaa/20200516_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_gluaa_glyaa_2_MMStack_Pos3_preproc_GL06/cropped_20200516_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_gluaa_glyaa_2_MMStack_Pos3_preproc_GL06.tif";
        String outputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20200626__datasets_for_work_on_ticket_021-improve-image-normalization/01_dany_datasets/20200516_bottom_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_gluaa_glyaa/20200516_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_gluaa_glyaa_2_MMStack_Pos3_preproc_GL06/";
        startMoma(inputPath, outputPath);
    }

    @Test
    public void _20200516_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_gluaa_glyaa_2_MMStack_Pos3_preproc_GL02() {
        // NOTES: * cropped image performs slightly better, but not good
//        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20200626__datasets_for_work_on_ticket_021-improve-image-normalization/01_dany_datasets/20200516_bottom_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_gluaa_glyaa/20200516_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_gluaa_glyaa_2_MMStack_Pos3_preproc_GL02/20200516_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_gluaa_glyaa_2_MMStack_Pos3_preproc_GL02.tif";
        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20200626__datasets_for_work_on_ticket_021-improve-image-normalization/01_dany_datasets/20200516_bottom_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_gluaa_glyaa/20200516_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_gluaa_glyaa_2_MMStack_Pos3_preproc_GL02/cropped_20200516_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_gluaa_glyaa_2_MMStack_Pos3_preproc_GL02.tif";
        String outputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20200626__datasets_for_work_on_ticket_021-improve-image-normalization/01_dany_datasets/20200516_bottom_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_gluaa_glyaa/20200516_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_gluaa_glyaa_2_MMStack_Pos3_preproc_GL06/";
        startMoma(inputPath, outputPath);
    }

    @Test
    public void _20191112_glc_spcm_1_MMStack_Pos7_preproc_GL15() {
//        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20191112_H07_bolA/20191112_glc_spcm_1_MMStack_Pos7_preproc_GL15/20191112_glc_spcm_1_MMStack_Pos7_preproc_GL15.tif";
//        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20191112_H07_bolA/20191112_glc_spcm_1_MMStack_Pos7_preproc_GL15/cropped_20191112_glc_spcm_1_MMStack_Pos7_preproc_GL15.tif";
        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20191112_H07_bolA/20191112_glc_spcm_1_MMStack_Pos7_preproc_GL15/cropped_smoothed_20191112_glc_spcm_1_MMStack_Pos7_preproc_GL15.tif";
//        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20191112_H07_bolA/20191112_glc_spcm_1_MMStack_Pos7_preproc_GL15/cropped_2_20191112_glc_spcm_1_MMStack_Pos7_preproc_GL15.tif";
//        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20191112_H07_bolA/20191112_glc_spcm_1_MMStack_Pos7_preproc_GL15/phc_channel_only__20191112_glc_spcm_1_MMStack_Pos7_preproc_GL15.tif";
        String outputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20191112_H07_bolA/20191112_glc_spcm_1_MMStack_Pos7_preproc_GL15/";
        startMoma(inputPath, outputPath);
    }

    @Test
    public void _20200528_glcIPTG_spcm_1_MMStack_Pos15_preproc_GL01() {
//        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20200626__datasets_for_work_on_ticket_021-improve-image-normalization/00_datasets_from_theo/01_bad_datasets/20200528_glcIPTG_spcm_1_MMStack_Pos15_preproc_GL01/20200528_glcIPTG_spcm_1_MMStack_Pos15_preproc_GL01.tif";
        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20200626__datasets_for_work_on_ticket_021-improve-image-normalization/00_datasets_from_theo/01_bad_datasets/20200528_glcIPTG_spcm_1_MMStack_Pos15_preproc_GL01/cropped_20200528_glcIPTG_spcm_1_MMStack_Pos15_preproc_GL01.tif";
//        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20200626__datasets_for_work_on_ticket_021-improve-image-normalization/00_datasets_from_theo/01_bad_datasets/20200528_glcIPTG_spcm_1_MMStack_Pos15_preproc_GL01/cropped_2_20200528_glcIPTG_spcm_1_MMStack_Pos15_preproc_GL01.tif";
//        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20200626__datasets_for_work_on_ticket_021-improve-image-normalization/00_datasets_from_theo/01_bad_datasets/20200528_glcIPTG_spcm_1_MMStack_Pos15_preproc_GL01/cropped_first_frames_20200528_glcIPTG_spcm_1_MMStack_Pos15_preproc_GL01.tif";
//        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20200626__datasets_for_work_on_ticket_021-improve-image-normalization/00_datasets_from_theo/01_bad_datasets/20200528_glcIPTG_spcm_1_MMStack_Pos15_preproc_GL01/phc_channel_only__20200528_glcIPTG_spcm_1_MMStack_Pos15_preproc_GL01.tif";
        String outputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20200626__datasets_for_work_on_ticket_021-improve-image-normalization/00_datasets_from_theo/01_bad_datasets/20200528_glcIPTG_spcm_1_MMStack_Pos15_preproc_GL01/";
        startMoma(inputPath, outputPath);
    }

    @Test
    public void _20180711_glyc_lactuloseTMG20uM_1_MMStack_Pos1_preproc_GL07() {
        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20200618_deepmoma_testing/20180711_glyc_lactuloseTMG20uM_1_MMStack_Pos1_preproc_GL07/cropped_20180711_glyc_lactuloseTMG20uM_1_MMStack_Pos1_preproc_GL07.tif";
        String outputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20200618_deepmoma_testing/20180711_glyc_lactuloseTMG20uM_1_MMStack_Pos1_preproc_GL07/";
        startMoma(inputPath, outputPath);
    }

    @Test
    public void _20180711_glyc_lactuloseTMG20uM_1_MMStack_Pos3_preproc_GL16() {
        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20200618_deepmoma_testing/20180711_glyc_lactuloseTMG20uM_1_MMStack_Pos3_preproc_GL16/cropped_20180711_glyc_lactuloseTMG20uM_1_MMStack_Pos3_preproc_GL16.tif";
        String outputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20200618_deepmoma_testing/20180711_glyc_lactuloseTMG20uM_1_MMStack_Pos3_preproc_GL16/";
        startMoma(inputPath, outputPath);
    }

    @Test
    public void _lugange_001() {
//        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20200422_test_lugange_dataset/series_01_crop_with_filamenting_cell.tif";
        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20200422_test_lugange_dataset/cropped_series_01_crop_with_filamenting_cell.tif";
        String outputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20200422_test_lugange_dataset/";
        startMoma(inputPath, outputPath);
    }

    @Test
    public void _20190515_hi1_med1_med2_rpmB_glu_gly_7_MMStack_Pos0_preproc_GL04() {
        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/Dany_20190515/20190515_hi1_med1_med2_rpmB_glu_gly_7_chr_hi1_deepmoma_2019-10-12_1d98d2f6/FAILED_20190515_hi1_med1_med2_rpmB_glu_gly_7_MMStack_Pos0_preproc_GL04_deepmoma/20190515_hi1_med1_med2_rpmB_glu_gly_7_MMStack_Pos0_preproc_GL04.tif";
        String outputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/Dany_20190515/20190515_hi1_med1_med2_rpmB_glu_gly_7_chr_hi1_deepmoma_2019-10-12_1d98d2f6/FAILED_20190515_hi1_med1_med2_rpmB_glu_gly_7_MMStack_Pos0_preproc_GL04_deepmoma/";
        startMoma(inputPath, outputPath);
    }

    @Test
    public void _20190515_hi1_med1_med2_rpmB_glu_gly_7_chr_rpmB_curated__dataset__Pos25_GL1() {
        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/Dany_20190515/20190515_hi1_med1_med2_rpmB_glu_gly_7_chr_hi1_deepmoma_2019-10-12_1d98d2f6/20190515_hi1_med1_med2_rpmB_glu_gly_7_chr_rpmB_curated__dataset__Pos25_GL1/images_phase_contrast_01_dany_20190515_hi1_med1_med2_rpmB_glu_gly_7_chr_rpmB_curated__dataset__Pos25_GL1__reshaped.tiff";
        String outputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/Dany_20190515/20190515_hi1_med1_med2_rpmB_glu_gly_7_chr_hi1_deepmoma_2019-10-12_1d98d2f6/20190515_hi1_med1_med2_rpmB_glu_gly_7_chr_rpmB_curated__dataset__Pos25_GL1/";
        startMoma(inputPath, outputPath);
    }



    @Test
    public void _cropped_420200403_5_rpmB_1_rrnB_synthrich7_1_MMStack_Pos13_preproc_GL16() {
        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/Dany_synthetic_rich_media/cropped_420200403_5_rpmB_1_rrnB_synthrich7_1_MMStack_Pos13_preproc_GL16.tif";
        String outputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/Dany_synthetic_rich_media/";
        startMoma(inputPath, outputPath);
    }

    @Test
    public void _20200417_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_richdefinedmedium_8_MMStack_Pos14_preproc_GL15() {
        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/05_inhomogeneous_phase_contrast_intensity/00_dany_20200417_top_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_richdefinedmedium/Pos14_GL15/20200417_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_richdefinedmedium_8_MMStack_Pos14_preproc_GL15.tif";
        String outputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/05_inhomogeneous_phase_contrast_intensity/00_dany_20200417_top_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_richdefinedmedium/Pos14_GL15/";
        startMoma(inputPath, outputPath);
    }

    @Test
    public void _20200417_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_richdefinedmedium_8_MMStack_Pos14_preproc_GL14() {
        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/05_inhomogeneous_phase_contrast_intensity/00_dany_20200417_top_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_richdefinedmedium/Pos14_GL14/20200417_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_richdefinedmedium_8_MMStack_Pos14_preproc_GL14.tif";
        String outputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/05_inhomogeneous_phase_contrast_intensity/00_dany_20200417_top_rpmB_rplN_rpsB_rrnB_hi1_hi3_med2_med3_richdefinedmedium/Pos14_GL14/";
        startMoma(inputPath, outputPath);
    }

    @Test
    public void _20190614_glu_lowLac_1_MMStack_Pos0_preproc_GL03() {
        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20190614_glu_lowLac_1_MMStack_Pos0_preproc_GL03/20190614_glu_lowLac_1_MMStack_Pos0_preproc_GL03.tif";
        String outputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20190614_glu_lowLac_1_MMStack_Pos0_preproc_GL03/output/";
        startMoma(inputPath, outputPath);
    }

    @Test
    public void _20190614_glu_lowLac_1_MMStack_Pos0_preproc_GL02() {
        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20190614_glu_lowLac_1_MMStack_Pos0_preproc_GL02/20190614_glu_lowLac_1_MMStack_Pos0_preproc_GL02__frames_220-250.tif";
        String outputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20190614_glu_lowLac_1_MMStack_Pos0_preproc_GL02/output/";
        startMoma(inputPath, outputPath);
    }

    @Test
    public void _new_10frames_20190424_hi2_hi3_med2_rplN_4_MMStack_Pos0_GL3() {
        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20190424_hi2_hi3_med2_rplN_4_MMStack_Pos0_GL3/new_10frames_20190424_hi2_hi3_med2_rplN_4_MMStack_Pos0_GL3.tif";
        String outputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20190424_hi2_hi3_med2_rplN_4_MMStack_Pos0_GL3/output/";
        startMoma(inputPath, outputPath);
    }

    @Test
    public void _50frames_20190424_hi2_hi3_med2_rplN_4_MMStack_Pos0_GL3() {
        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20190424_hi2_hi3_med2_rplN_4_MMStack_Pos0_GL3/50frames_20190424_hi2_hi3_med2_rplN_4_MMStack_Pos0_GL3.tiff";
        String outputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20190424_hi2_hi3_med2_rplN_4_MMStack_Pos0_GL3/output/";
        startMoma(inputPath, outputPath);
    }

    @Test
    public void _450frames_20190424_hi2_hi3_med2_rplN_4_MMStack_Pos0_GL3() {
        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20190424_hi2_hi3_med2_rplN_4_MMStack_Pos0_GL3/450frames_20190424_hi2_hi3_med2_rplN_4_MMStack_Pos0_GL3.tif";
        String outputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20190424_hi2_hi3_med2_rplN_4_MMStack_Pos0_GL3/output/";
        startMoma(inputPath, outputPath);
    }

    @Test
    public void _20190424_hi2_hi3_med2_rplN_4_MMStack_Pos0_GL3() {
        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20190424_hi2_hi3_med2_rplN_4_MMStack_Pos0_GL3/20190424_hi2_hi3_med2_rplN_4_MMStack_Pos0_GL3.tiff";
//        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20190424_hi2_hi3_med2_rplN_4_MMStack_Pos0_GL3/cropped_20190424_hi2_hi3_med2_rplN_4_MMStack_Pos0_GL3.tiff";
        String outputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20190424_hi2_hi3_med2_rplN_4_MMStack_Pos0_GL3/output/";
        startMoma(inputPath, outputPath);
    }

    @Test
    public void _cropped_20190424_hi2_hi3_med2_rplN_4_MMStack_Pos0_GL3() {
        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20190424_hi2_hi3_med2_rplN_4_MMStack_Pos0_GL3/cropped_20190424_hi2_hi3_med2_rplN_4_MMStack_Pos0_GL3.tiff";
        String outputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20190424_hi2_hi3_med2_rplN_4_MMStack_Pos0_GL3/output/";
        startMoma(inputPath, outputPath);
    }

    @Test
    public void headless_20190515_hi1_med1_med2_rpmB_glu_gly_7_MMStack_Pos25_preproc_GL01__frames_400_450() {
        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20190515_hi1_med1_med2_rpmB_glu_gly_7_MMStack_Pos25_preproc_GL01/20190515_hi1_med1_med2_rpmB_glu_gly_7_MMStack_Pos25_preproc_GL01__frames_400-450.tif";
        String outputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20190515_hi1_med1_med2_rpmB_glu_gly_7_MMStack_Pos25_preproc_GL01/output_headless/";
        MoMA moma = new MoMA();
        moma.HEADLESS = true;
        MoMA.main(new String[]{"-i", inputPath, "-o", outputPath});
    }

    @Test
    public void _20190515_hi1_med1_med2_rpmB_glu_gly_7_MMStack_Pos25_preproc_GL01__frames_400_450() {
        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20190515_hi1_med1_med2_rpmB_glu_gly_7_MMStack_Pos25_preproc_GL01/20190515_hi1_med1_med2_rpmB_glu_gly_7_MMStack_Pos25_preproc_GL01__frames_400-450.tif";
        String outputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20190515_hi1_med1_med2_rpmB_glu_gly_7_MMStack_Pos25_preproc_GL01/output/";
        startMoma(inputPath, outputPath);
//        startMoma(new String[]{"-i", inputPath, "-o", outputPath, "-p", "/home/micha/Documents/01_work/git/MoMA/mm.properties"});
    }

    @Test
    public void _20190515_hi1_med1_med2_rpmB_glu_gly_7_MMStack_Pos25_preproc_GL01() {
        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20190515_hi1_med1_med2_rpmB_glu_gly_7_MMStack_Pos25_preproc_GL01/20190515_hi1_med1_med2_rpmB_glu_gly_7_MMStack_Pos25_preproc_GL01.tif";
        String outputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20190515_hi1_med1_med2_rpmB_glu_gly_7_MMStack_Pos25_preproc_GL01/output/";
        startMoma(inputPath, outputPath);
    }

    @Test
    public void _20191105_glc_spcm_1_MMStack_Pos7_preproc_GL15() {
        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20191105_glc_spcm_1_MMStack_Pos7_preproc_GL15/20191105_glc_spcm_1_MMStack_Pos7_preproc_GL15.tif";
        String outputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20191105_glc_spcm_1_MMStack_Pos7_preproc_GL15/output/";
        startMoma(inputPath, outputPath);
    }

    @Test
    public void _cropped_20191105_glc_spcm_1_MMStack_Pos7_preproc_GL15() {
        String inputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20191105_glc_spcm_1_MMStack_Pos7_preproc_GL15/cropped_20191105_glc_spcm_1_MMStack_Pos7_preproc_GL15.tif";
        String outputPath = "/home/micha/Documents/01_work/git/MoMA/test_datasets/20191105_glc_spcm_1_MMStack_Pos7_preproc_GL15/output/";
        startMoma(inputPath, outputPath);
    }

    private void startMoma(String inputPath, String outputPath, Integer tmin, Integer tmax){
        remove_probability_maps(inputPath);
        create_output_folder(outputPath);
        String[] args = new String[]{"-i", inputPath, "-o", outputPath, "-tmin", tmin.toString(), "-tmax", tmax.toString()};
        MoMA moma = new MoMA();
        moma.HEADLESS = false;
        MoMA.main(args);
    }

    private void startMoma(String inputPath, String outputPath){
        remove_probability_maps(inputPath);
        create_output_folder(outputPath);
        String[] args = new String[]{"-i", inputPath, "-o", outputPath};
        MoMA moma = new MoMA();
        moma.HEADLESS = false;
        MoMA.main(args);
    }

    private void create_output_folder(String outputPath) {
        File file = new File(outputPath);
        file.mkdir();
    }

    /**
     * Delete preexisting probability maps. During testing, we often want to test the generation
     * of the probability maps, which are cached to disk and loaded, if they exist for a given model.
     * This function removes those cached files to always run the U-Net preprocessing.
     * @param path
     */
    private void remove_probability_maps(String path) {
        PathMatcher matcher =
                FileSystems.getDefault().getPathMatcher("glob:*__model_*.tif*");
        File f = new File(path);
        File parentFolder = new File(f.getParent());

        String[] pathnames = parentFolder.list();
        for (String name : pathnames) {
            String filePath = parentFolder + "/" + name;
            if (matcher.matches(Paths.get(name))) {
                System.out.print(filePath);
                File f2 = new File(filePath);
                if (f2.delete())                      //returns Boolean value
                {
                    System.out.println("Deleted: " + f.getName());   //getting and printing the file name
                } else {
                    System.out.println("Failed to delete: " + f.getName());
                }
            }
        }
    }
}
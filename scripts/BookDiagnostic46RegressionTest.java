import com.vobichngan.kanzi.LayoutReadingPlanner;
import com.vobichngan.kanzi.LayoutRegion;
import com.vobichngan.kanzi.ReadingOrder;
import com.vobichngan.kanzi.StrictOcrOrderGuard;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Collections;

/**
 * Real 720x1280 single-page camera geometry: 46 OCR boxes, notes at right,
 * page number at bottom right, ONE large PP-DocLayout-S region.
 * User-provided geometry/confidences only; book text is not copied.
 */
public final class BookDiagnostic46RegressionTest {
    private static final String DATA = "1 .2536 243 385 359 401 355 425 240 409\n" +
        "2 .8551 104 425 269 419 270 446 105 452\n" +
        "3 .9741 118 437 334 447 333 472 117 462\n" +
        "4 .6881 435 449 508 449 508 463 435 463\n" +
        "5 .2896 397 466 424 466 424 477 397 477\n" +
        "6 .4414 436 460 509 460 509 474 436 474\n" +
        "7 .8148 437 473 510 473 510 484 437 484\n" +
        "8 .7245 97 485 150 476 154 495 100 504\n" +
        "9 .9465 436 485 510 483 510 496 436 497\n" +
        "10 .9634 438 496 463 496 463 508 438 508\n" +
        "11 .9607 108 529 433 546 431 574 107 556\n" +
        "12 .3160 343 529 428 537 426 553 342 545\n" +
        "13 .9729 105 577 257 576 257 594 105 595\n" +
        "14 .2601 360 570 430 574 429 589 359 585\n" +
        "15 .9811 103 590 435 605 434 632 102 617\n" +
        "16 .1785 398 592 431 592 431 608 398 608\n" +
        "17 .9387 82 617 277 615 277 639 82 641\n" +
        "18 .9838 100 637 294 641 294 660 100 656\n" +
        "19 .6016 315 630 328 630 328 638 315 638\n" +
        "20 .9667 96 656 437 668 437 693 96 681\n" +
        "21 .1394 468 651 521 651 521 661 468 661\n" +
        "22 .9976 0 666 17 666 17 685 0 685\n" +
        "23 .1257 446 677 524 676 525 690 446 691\n" +
        "24 .9692 0 689 15 689 15 706 0 706\n" +
        "25 .9900 91 703 438 711 438 733 90 725\n" +
        "26 .9176 447 697 527 698 527 712 447 711\n" +
        "27 .9695 448 711 528 711 528 725 448 725\n" +
        "28 .9990 69 726 440 730 439 757 69 752\n" +
        "29 .9522 449 725 529 725 529 739 449 739\n" +
        "30 .9934 449 739 514 739 514 753 449 753\n" +
        "31 .9862 67 751 439 753 439 778 67 776\n" +
        "32 .9992 65 776 442 778 441 801 65 799\n" +
        "33 .9623 64 800 441 801 441 823 64 822\n" +
        "34 .9462 81 826 442 827 442 845 81 844\n" +
        "35 .9894 78 851 443 853 443 871 78 870\n" +
        "36 .9888 76 877 444 878 444 897 76 895\n" +
        "37 .9956 55 905 305 903 305 922 55 924\n" +
        "38 .4200 377 904 445 904 445 919 377 919\n" +
        "39 .9636 71 931 447 927 448 946 71 950\n" +
        "40 .9922 68 958 447 954 448 973 68 977\n" +
        "41 .9310 45 986 448 979 448 1000 45 1006\n" +
        "42 .7767 62 1014 450 1006 450 1026 63 1035\n" +
        "43 .9634 61 1043 451 1033 452 1055 61 1065\n" +
        "44 .9956 49 1072 452 1062 453 1085 50 1095\n" +
        "45 1.0000 491 1105 518 1105 518 1122 491 1122\n" +
        "46 .1153 484 1122 528 1120 529 1139 484 1140";
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static int pos(LayoutReadingPlanner.Plan plan,int id) {
        for (LayoutReadingPlanner.Placement p:plan.placements) {
            if (p.ocrId==id) return p.readingIndex;
        }
        return -1;
    }
    private static LayoutReadingPlanner.Plan execute(List<LayoutRegion> regions) {
        List<StrictOcrOrderGuard.Item> source=new ArrayList<>();
        List<ReadingOrder.Box> boxes=new ArrayList<>();
        for (String line:DATA.split("\n")) {
            String[] t=line.trim().split(" +");
            check(t.length==10,"Malformed OCR geometry fixture: "+line);
            int id=Integer.parseInt(t[0]);
            float conf=Float.parseFloat(t[1]);
            float[] x=new float[4], y=new float[4];
            for (int k=0;k<4;k++) {
                x[k]=Float.parseFloat(t[2+2*k]);
                y[k]=Float.parseFloat(t[3+2*k]);
            }
            String anonymous="OCR_BOX_"+id;
            source.add(new StrictOcrOrderGuard.Item(id,anonymous));
            boxes.add(new ReadingOrder.Box(anonymous,conf,x,y));
        }
        check(boxes.size()==46,"All real OCR boxes must be present");
        return LayoutReadingPlanner.plan(source,boxes,regions);
    }
    public static void main(String[] args) {
        List<LayoutRegion> broad=Collections.singletonList(
            new LayoutRegion(2,.98f,0,380,540,1150));
        for (List<LayoutRegion> region:Arrays.asList(broad,Collections.<LayoutRegion>emptyList())) {
            LayoutReadingPlanner.Plan p=execute(region);
            check(p.verified.accepted,"Strict OCR ID permutation check failed");
            check(p.verified.ordered.size()==46,"OCR content lost");
            check(pos(p,2)<pos(p,3),"body reading order broken at top");
            check(pos(p,3)<pos(p,11),"body line 3 before next paragraph");
            check(pos(p,11)<pos(p,15),"body paragraph order broken");
            check(pos(p,25)<pos(p,28),"main body order broken");
            check(pos(p,36)<pos(p,37),"sentence completion broken");
            check(pos(p,37)<pos(p,39),"body continuation broken");
            check(pos(p,44)<pos(p,4),
                "right-side footnotes must not be read before the main body: "+
                p.verified.exactText());
            check(pos(p,44)<pos(p,26),
                "second footnote block must not interrupt main body");
            check(pos(p,44)<pos(p,45),
                "bottom-right page number must not precede last body line");
            if (!region.isEmpty()) check(p.distinctAssignedRegions==1,
                "One broad region must not be described as separate page zones");
        }
        System.out.println("REAL_SINGLE_PAGE_46_ORDER=PASS");
    }
}

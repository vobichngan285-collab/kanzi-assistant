import com.vobichngan.kanzi.LayoutReadingPlanner;
import com.vobichngan.kanzi.LayoutRegion;
import com.vobichngan.kanzi.ReadingOrder;
import com.vobichngan.kanzi.StrictOcrOrderGuard;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Anonymized geometry from an actual user-provided 720x1280 book diagnosis:
 * 52 boxes, some heavily skewed, left/right pages, edge OCR noise, one
 * PP-DocLayout-S covering region. Literal book text is intentionally omitted.
 * Exact original quadrilateral coordinates + OCR confidence are retained.
 */
public final class BookDiagnostic52RegressionTest {
    private static final String RAW =
        "1 .6438 173 402 245 396 247 412 174 418\n" +
        "2 .6054 451 401 524 415 521 431 448 416\n" +
        "3 .7676 142 427 221 423 222 432 143 437\n" +
        "4 .3270 141 437 163 437 163 445 141 445\n" +
        "5 .3638 160 436 246 429 247 440 161 447\n" +
        "6 .5755 250 430 269 430 269 437 250 437\n" +
        "7 .3224 132 447 193 444 193 454 132 457\n" +
        "8 .5627 134 456 200 453 201 465 134 467\n" +
        "9 .4513 131 466 267 457 268 470 132 479\n" +
        "10 .3398 114 476 280 467 281 481 115 491\n" +
        "11 .3930 583 470 612 470 612 477 583 477\n" +
        "12 .4524 110 486 263 479 263 494 111 501\n" +
        "13 .3367 110 498 162 498 162 508 110 508\n" +
        "14 .5341 119 508 242 502 242 515 119 520\n" +
        "15 .6021 114 530 213 527 213 539 114 542\n" +
        "16 .2787 445 532 471 536 469 546 443 542\n" +
        "17 .7472 465 524 571 543 569 554 463 535\n" +
        "18 .8269 367 535 481 545 480 561 366 551\n" +
        "19 .1703 551 532 568 532 568 540 551 540\n" +
        "20 .7243 111 543 139 543 139 551 111 551\n" +
        "21 .9218 363 552 397 552 397 563 363 563\n" +
        "22 .8012 398 547 574 576 571 591 396 562\n" +
        "23 .4514 86 576 232 572 232 585 86 589\n" +
        "24 .8545 79 600 256 596 257 610 79 614\n" +
        "25 .4426 581 600 601 602 600 611 580 609\n" +
        "26 .3643 604 595 630 598 629 607 603 603\n" +
        "27 .5929 89 613 157 613 157 624 89 624\n" +
        "28 .3611 581 608 632 613 631 622 580 618\n" +
        "29 .7965 87 623 277 623 277 637 87 637\n" +
        "30 .1093 582 616 613 620 612 628 581 625\n" +
        "31 .7542 68 637 244 635 245 648 69 651\n" +
        "32 .7592 79 650 268 649 268 662 79 663\n" +
        "33 .8775 62 663 169 665 169 677 62 675\n" +
        "34 .2242 166 668 175 668 175 673 166 673\n" +
        "35 .8176 53 685 297 691 296 711 52 705\n" +
        "36 .8634 344 693 440 696 440 712 343 708\n" +
        "37 .4010 0 712 43 714 42 726 0 724\n" +
        "38 .2993 2 705 45 705 45 716 2 716\n" +
        "39 .8563 51 707 168 707 168 718 51 718\n" +
        "40 .9536 62 712 294 720 293 741 61 734\n" +
        "41 .3629 0 723 39 723 39 734 0 734\n" +
        "42 .5321 1 732 36 733 36 744 1 742\n" +
        "43 .9330 41 726 293 735 292 757 40 748\n" +
        "44 .3626 0 741 31 742 30 754 0 752\n" +
        "45 .9123 38 742 289 752 289 771 37 762\n" +
        "46 .9524 341 735 491 746 489 764 340 752\n" +
        "47 .9861 342 751 471 760 470 778 341 768\n" +
        "48 .6640 541 746 581 751 580 763 540 758\n" +
        "49 .9829 35 757 288 767 288 787 34 777\n" +
        "50 .9819 341 763 586 791 583 813 338 784\n" +
        "51 .4088 514 757 582 766 581 778 512 769\n" +
        "52 .5852 1 786 8 786 8 792 1 792";

    private static void require(boolean ok,String message) {
        if(!ok)throw new AssertionError(message);
    }
    private static int at(List<LayoutReadingPlanner.Placement> placements,int id) {
        for(LayoutReadingPlanner.Placement p:placements)
            if(p.ocrId==id)return p.readingIndex;
        return -1;
    }
    public static void main(String[] args) {
        List<StrictOcrOrderGuard.Item> source=new ArrayList<>();
        List<ReadingOrder.Box> boxes=new ArrayList<>();
        for(String raw:RAW.split("\n")) {
            String[] fields=raw.trim().split(" +");
            require(fields.length==10,"bad fixture row: "+raw);
            int id=Integer.parseInt(fields[0]);
            float conf=Float.parseFloat(fields[1]);
            float[] x=new float[4],y=new float[4];
            for(int k=0;k<4;k++) {
                x[k]=Float.parseFloat(fields[2+2*k]);
                y[k]=Float.parseFloat(fields[3+2*k]);
            }
            String text="RAW_OCR_BOX_"+id;
            source.add(new StrictOcrOrderGuard.Item(id,text));
            boxes.add(new ReadingOrder.Box(text,conf,x,y));
        }
        require(boxes.size()==52,"all 52 original quadrilaterals are required");
        // Replicate the one large layout region, which was wrongly advertised
        // as 52 successful region assignments despite not finding two pages.
        List<LayoutRegion> single=Collections.singletonList(
            new LayoutRegion(2,.95f,0,380,680,840));
        LayoutReadingPlanner.Plan candidate=LayoutReadingPlanner.plan(source,boxes,single);
        require(candidate.verified.accepted,"strict ID permutation rejected");
        require(candidate.distinctAssignedRegions==1,
            "one AI-wide region must not be reported as multiple distinct page regions");
        require(candidate.assignedBoxes==52,
            "all boxes should remain traceable to the one broad region");
        require(candidate.geometryGroupOrder && candidate.geometryGroups>=2,
            "real two-page fallback must be explicitly reported as geometric grouping");
        require(candidate.placements.size()==52,"an OCR box was dropped");
        require(candidate.verified.ordered.size()==52,"not all original raw items remain");
        // Two-page reading: a late left-page body line must precede the right
        // page's first body line, even though the right starts higher on Y.
        require(at(candidate.placements,49)<at(candidate.placements,17),
            "left page must finish before right page body");
        require(at(candidate.placements,18)<at(candidate.placements,21),
            "right page's preceding body line must not follow a later fragment");
        require(at(candidate.placements,21)<at(candidate.placements,22),
            "right page same-line fragment must follow leftmost fragment");
        require(at(candidate.placements,52)>0,
            "bottom-left isolated OCR should not be first utterance");
        require(new java.util.HashSet<Integer>(
            Arrays.asList(candidate.placements.stream().map(p->p.ocrId)
                .toArray(Integer[]::new))).size()==52,
            "each original OCR box must be preserved once");
        System.out.println("REAL_BOOK_52_ORDER=PASS");
    }
}

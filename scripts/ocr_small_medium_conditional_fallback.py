#!/usr/bin/env python3
"""Offline evaluation of selective Medium recognition on the exact same boxes.

This is a COUNTERFACTUAL analysis using predictions of both already-computed
recognizers. A phone implementation would FIRST run Small recognition, THEN
conditionally run Medium, adding latency only on selected boxes. This experiment
does not modify app code, and confidence is not proof of correctness.

The thresholds are predeclared (no use of GT when picking a policy).
Ground-truth text is consulted solely for paired error evaluation.
"""
import argparse,collections,csv,json
from pathlib import Path
from ocr_box_value_deep_dive import normalize,distance

def load_csv(p):
    with open(p,encoding="utf-8-sig",newline="") as f:
        return list(csv.DictReader(f))

POLICIES={
    "small_only":lambda s:False,
    "medium_all_direct":lambda s:True,
    "fallback_if_small_empty":lambda s:not s["predicted_text"].strip(),
    "fallback_small_conf_lt_0_85":lambda s:float(s["rec_conf"])<.85,
    "fallback_small_conf_lt_0_95":lambda s:float(s["rec_conf"])<.95,
    "fallback_small_conf_lt_0_99":lambda s:float(s["rec_conf"])<.99,
    "fallback_small_conf_lt_0_995":lambda s:float(s["rec_conf"])<.995,
    "fallback_small_conf_lt_0_999":lambda s:float(s["rec_conf"])<.999,
    "fallback_small_short_and_low_conf":lambda s:
        len(normalize(s["predicted_text"]))<=3 and float(s["rec_conf"])<.98,
}

def quantile(values,p):
    if not values:return None
    arr=sorted(values)
    return round(arr[int((len(arr)-1)*p)],3)

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--box-csv",required=True)
    parser.add_argument("--photo-csv",required=True)
    parser.add_argument("--cohort",required=True)
    parser.add_argument("--out",required=True)
    args=parser.parse_args()
    out=Path(args.out);out.mkdir(parents=True,exist_ok=True)
    boxes=load_csv(args.box_csv)
    photos=load_csv(args.photo_csv)
    assert len(photos)>=110
    lookup=collections.defaultdict(dict)
    for row in boxes:
        key=(row["split"],row["image"],row["box_index"])
        lookup[key][row["mode"]]=row
    assert all(len(x)==2 for x in lookup.values())
    gt_pairs=[item for item in lookup.values() if int(item["small_det_small_rec"]["gt_matched"])]
    assert len(gt_pairs)>=200,len(gt_pairs)
    print("FALLBACK_PAIRED_GT",args.cohort,"photos",len(photos),
          "recognized_boxes",len(lookup),"GT",len(gt_pairs),flush=True)

    chosen=[]
    for policy,decide in POLICIES.items():
        medium_frames=collections.defaultdict(float)
        requests=0;unknown_switches=0;errors=0;all_chars=0;exact=0
        worsened=0;improved=0;equal=0
        for (split,img,box),pair in lookup.items():
            small=pair["small_det_small_rec"]
            medium=pair["small_det_medium_rec"]
            yes=decide(small)
            chosen_mode="small_det_medium_rec" if yes else "small_det_small_rec"
            c=pair[chosen_mode]
            if yes:
                requests+=1
                if policy!="medium_all_direct":
                    medium_frames[split,img]+=float(medium["rec_ms"])
                if not int(c["gt_matched"]) and small["predicted_text"]!=medium["predicted_text"]:
                    unknown_switches+=1
            if int(c["gt_matched"]):
                errs=int(c["edit_distance"])
                errs_small=int(small["edit_distance"])
                errors+=errs
                all_chars+=int(c["gt_chars"])
                exact+=errs==0
                improved+=errs<errs_small
                worsened+=errs>errs_small
                equal+=errs==errs_small
        perframe=[]
        for frame in photos:
            key=frame["split"],frame["image"]
            baseline=float(frame["total_small_ms"])
            full_medium=float(frame["total_mixed_ms"])
            if policy=="medium_all_direct":
                total=full_medium
            else:
                total=baseline+medium_frames.get(key,0.)
            perframe.append((total,baseline,full_medium))
        times=[a for a,b,c in perframe]
        baseline=[b for a,b,c in perframe]
        result={
            "cohort":args.cohort,"policy":policy,"photos":len(photos),
            "matched_GT":len(gt_pairs),
            "all_boxes":len(lookup),
            "medium_rec_uses":requests,
            "medium_fraction":round(requests/max(1,len(lookup)),4),
            "matched_CER":round(errors/max(1,all_chars),5),
            "matched_exact":exact,
            "matched_exact_rate":round(exact/max(1,len(gt_pairs)),5),
            "improved_GT_vs_small":improved,"worsened_GT_vs_small":worsened,
            "unchanged_GT_vs_small":equal,
            "unknown_changed_text":unknown_switches,
            "cloud_total_pipeline_p50_ms":quantile(times,.5),
            "cloud_total_pipeline_p95_ms":quantile(times,.95),
            "cloud_relative_sum_compute":round(sum(times)/max(1,sum(baseline)),4),
            "cloud_per_photo_extra_p95_ms":quantile([max(0,a-b) for a,b,c in perframe],.95),
        }
        chosen.append(result)
        print("SELECTIVE_MEDIUM_POLICY",json.dumps(result,ensure_ascii=False),flush=True)
    with (out/"policy_sweep.csv").open("w",encoding="utf-8-sig",newline="") as f:
        writer=csv.DictWriter(f,fieldnames=list(chosen[0]))
        writer.writeheader();writer.writerows(chosen)
    (out/"README.txt").write_text(
        "All model outputs came from the SAME detector/crop and original SHA-verified models.\n"
        "Confidence gating cannot guarantee correctness; simulation does not demonstrate phone latency.\n"
        "Mixed direct strategy runs Medium alone. Fallback strategies run Small FIRST plus Medium sometimes.\n"
        "GT-unmatched box text changes are unknown, not automatically false speech.\n"
        "Predeclared cutoff policies; independent holdout must not be retuned after seeing results.\n"
    )
    print("SELECTIVE_MEDIUM_COUNTERFACTUAL=PASS",args.cohort,len(chosen),flush=True)

if __name__=="__main__":main()

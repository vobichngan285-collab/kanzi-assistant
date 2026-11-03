#!/usr/bin/env python3
"""Predeclared model rescue budgets, one Small detector, NO APK.

Input: exact same annotated photo boxes that ran Small and Medium ONNX,
with real per-box REC CPU times. Decisions use ONLY Small output, confidence
and reading order, never GT. GT evaluates consequences after selection.

Policies compare priority lowest confidence vs reading order, plus budget
0/1/2/3 (Medium calls per frame). Original Python cloud benchmark uses RGB
and is not exact Android SDK BGR; this is a policy/latency proxy only.
"""
import argparse,collections,csv,json,math
from pathlib import Path

SMALL="small_det_small_rec"
MED="small_det_medium_rec"
THRESH=.95
BUDGETS=[0,1,2,3,5,99]

def load(path):
    with open(path,encoding="utf-8-sig",newline="") as f:
        return list(csv.DictReader(f))

def percentile(xs,q):
    if not xs:return None
    vals=sorted(xs)
    return round(vals[round((len(vals)-1)*q)],3)

def policy_run(pairs,photos,cap,priority):
    groups=collections.defaultdict(list)
    for key,entry in pairs.items():
        groups[key[:2]].append((key,entry))
    by_photo=[]
    selected_gt=[]
    changed_unknown=0
    last_budget=0
    for row in photos:
        image=(row["split"],row["image"])
        candidates=groups.get(image,[])
        candidates.sort(key=lambda v:int(v[0][2]))
        uncertain=[(key,entry) for key,entry in candidates
                   if float(entry[SMALL]["rec_conf"])<THRESH]
        if priority=="lowest_confidence":
            uncertain.sort(key=lambda z:(float(z[1][SMALL]["rec_conf"]),int(z[0][2])))
        elif priority!="reading_order":
            raise ValueError(priority)
        chosen={key for key,entry in uncertain[:cap]}
        assert len(chosen)<=cap
        extra=0.
        for key,entry in candidates:
            sm=entry[SMALL];md=entry[MED]
            is_medium=key in chosen
            pick=md if is_medium else sm
            if is_medium:
                extra+=float(md["rec_ms"])
                if not int(sm["gt_matched"]) and sm["predicted_text"]!=md["predicted_text"]:
                    changed_unknown+=1
            if int(sm["gt_matched"]):
                selected_gt.append((sm,pick))
        last_budget+=len(chosen)
        baseline=float(row["total_small_ms"])
        by_photo.append({
            "small_ms":baseline,
            "hybrid_ms":baseline+extra,
            "medium_extra_ms":extra,
            "medium_calls":len(chosen),
            "uncertain":len(uncertain),
        })
    assert len(selected_gt)>200
    gt_chars=sum(int(sm["gt_chars"]) for sm,pick in selected_gt)
    sm_edits=sum(int(sm["edit_distance"]) for sm,pick in selected_gt)
    hybrid_edits=sum(int(pick["edit_distance"]) for sm,pick in selected_gt)
    return {
        "photos":len(photos),
        "gt_paired_lines":len(selected_gt),
        "gt_chars":gt_chars,
        "original_cer":round(sm_edits/gt_chars,5),
        "policy_cer":round(hybrid_edits/gt_chars,5),
        "original_exact":sum(int(sm["edit_distance"])==0 for sm,pick in selected_gt),
        "policy_exact":sum(int(pick["edit_distance"])==0 for sm,pick in selected_gt),
        "gt_improved":sum(int(pick["edit_distance"])<int(sm["edit_distance"]) for sm,pick in selected_gt),
        "gt_worsened":sum(int(pick["edit_distance"])>int(sm["edit_distance"]) for sm,pick in selected_gt),
        "unknown_text_swaps":changed_unknown,
        "total_medium_calls":last_budget,
        "medium_calls_per_photo":round(last_budget/len(photos),3),
        "photos_with_uncertain_more_than_cap":sum(x["uncertain"]>cap for x in by_photo),
        "cloud_cpu_pipeline_p50_ms":percentile([x["hybrid_ms"] for x in by_photo],.50),
        "cloud_cpu_pipeline_p95_ms":percentile([x["hybrid_ms"] for x in by_photo],.95),
        "cloud_cpu_pipeline_p99_ms":percentile([x["hybrid_ms"] for x in by_photo],.99),
        "extra_rec_infer_p95_ms":percentile([x["medium_extra_ms"] for x in by_photo],.95),
        "total_cpu_ratio_vs_small":round(sum(x["hybrid_ms"] for x in by_photo)/sum(x["small_ms"] for x in by_photo),4),
    }

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--boxes",required=True)
    p.add_argument("--photos",required=True)
    p.add_argument("--cohort",required=True)
    p.add_argument("--out",required=True)
    args=p.parse_args()
    out=Path(args.out);out.mkdir(exist_ok=True,parents=True)
    b=load(args.boxes);photos=load(args.photos)
    assert len(photos)>110 and len(b)>500
    pairs=collections.defaultdict(dict)
    for x in b:
        k=(x["split"],x["image"],x["box_index"])
        pairs[k][x["mode"]]=x
    assert all(SMALL in v and MED in v for v in pairs.values())
    assert len(set((x["split"],x["image"]) for x in photos))==len(photos)
    output=[]
    for cap in BUDGETS:
        for priority in ("reading_order","lowest_confidence"):
            result={"cohort":args.cohort,"max_medium_per_frame":cap,
                    "selection":priority,
                    **policy_run(pairs,photos,cap,priority)}
            output.append(result)
            print("RESCUE_BUDGET_REAL_PHOTO_AB",json.dumps(result,ensure_ascii=False),flush=True)
    with (out/"rescue_budget_results.csv").open("w",newline="",encoding="utf-8-sig") as f:
        w=csv.DictWriter(f,fieldnames=list(output[0]))
        w.writeheader();w.writerows(output)
    (out/"warnings.txt").write_text(
      "All inputs are previously completed public real-scene OCR predictions from the same Small detector.\n"
      "The base study used a Python RGB approximation, not current Android SDK BGR.\n"
      "A candidate cap and mode change were tested OFFLINE, not integrated into camera processing.\n"
      "Per-image CPU cost adds measured Medium ONNX inference; excludes Android TTS/camera, crop and scheduling changes.\n"
      "No GT is used to choose which box is retried, only to score outcomes.\n"
      "The 99 budget approximates all low confidence candidates; avoid treating it as Android policy.\n")
    print("ANDROID_12GB_RESCUE_POLICY_SWEEP=PASS",args.cohort,len(output),flush=True)

if __name__=="__main__":main()

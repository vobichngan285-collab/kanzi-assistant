#!/usr/bin/env python3
"""Cross-tab 111 annotation-unmatched cases against already recognized high boxes.

This gives a falsifiable review priority, not a human visual verdict.
No user image or third-party photo bytes are stored or republished.
"""
import collections,csv,json,sys
from pathlib import Path

def load(path):
    with open(path,encoding="utf-8-sig",newline="") as fp:
        return list(csv.DictReader(fp))

def main():
    evidence=load(sys.argv[1])
    baseline=load(sys.argv[2])
    out=Path(sys.argv[3]);out.mkdir(parents=True,exist_ok=True)
    join={(r["split"],str(r["image"]),str(r["candidate_id"])):r for r in baseline}
    cases=[]
    for r in evidence:
        key=(r["split"],str(r["image"]),str(r["candidate_id"]))
        assert key in join,key
        x=join[key]
        covered=float(x["high_box_overlap_max"])
        n=int(r["OCR_len"])
        conf=float(r["OCR_confidence"])
        hypothesis=r["geometric_hypothesis"]
        # Rank only for manual review, do not use GT to choose live OCR.
        priority=0
        if hypothesis=="far_from_annotated_text":priority+=3
        elif hypothesis=="near_annotated_text_no_overlap":priority+=2
        if n>=2:priority+=2
        if conf>=.75:priority+=2
        if covered<.1:priority+=2
        if r["text_match_to_adjacent_GT"]!="none":priority+=1
        case=dict(r)
        case.update({
          "max_overlap_existing_high_box":round(covered,4),
          "overlaps_existing_high_ge_50pct":int(covered>=.5),
          "spatially_independent_of_high_box":int(covered<.1),
          "human_review_priority_score":priority,
          "reviewed_by_human":False,
        })
        cases.append(case)
    assert len(cases)==111
    groups=collections.defaultdict(list)
    for r in cases:groups[r["geometric_hypothesis"]].append(r)
    summary=[]
    for category,rows in sorted(groups.items(),key=lambda item:-len(item[1])):
        total=len(rows)
        record={
          "geometric_class":category,
          "total":total,
          "overlap_high_gt50pct":sum(int(x["overlaps_existing_high_ge_50pct"]) for x in rows),
          "independent_high_lt10pct":sum(int(x["spatially_independent_of_high_box"]) for x in rows),
          "nonempty_recognition":sum(int(x["OCR_len"])>0 for x in rows),
          "two_plus_chars":sum(int(x["OCR_len"])>=2 for x in rows),
          "two_plus_conf75":sum(int(x["OCR_len"])>=2 and float(x["OCR_confidence"])>=.75 for x in rows),
          "probably_valid_nearby_text_same_or_substring":sum(x["text_match_to_adjacent_GT"] in
             ("exact","gt_substring_of_ocr","ocr_substring_of_near_gt") for x in rows),
        }
        summary.append(record)
        print("UNMATCHED_BOX_CROSS_TAB",json.dumps(record,ensure_ascii=False),flush=True)
    shortlist=sorted(cases,key=lambda r:(-int(r["human_review_priority_score"]),
                             -float(r["OCR_confidence"]),r["split"],int(r["image"])))
    for case in shortlist[:25]:
        print("PRIORITY_VISUAL_REVIEW_CANDIDATE",json.dumps({
          "image":case["split"]+":"+str(case["image"]),
          "boxscore":case["box_score"],"prediction":case["OCR_result"],
          "geometry":case["geometric_hypothesis"],
          "confidence":case["OCR_confidence"],"existing_overlap":case["max_overlap_existing_high_box"],
          "nearest_GT":case["nearest_GT_text"],"priority":case["human_review_priority_score"]},
           ensure_ascii=False),flush=True)
    with (out/"unmatched_111_cross_tab.csv").open("w",newline="",encoding="utf-8-sig") as f:
        w=csv.DictWriter(f,fieldnames=list(summary[0]));w.writeheader();w.writerows(summary)
    with (out/"priority_review_queue.csv").open("w",newline="",encoding="utf-8-sig") as f:
        w=csv.DictWriter(f,fieldnames=list(shortlist[0]));w.writeheader();w.writerows(shortlist)
    (out/"readme.txt").write_text(
        "These are candidate machine hypotheses only, NOT a visual or human accuracy audit.\n"
        "The priority queue can guide later authorized visual inspection of original images.\n"
        "No default OCR threshold changes, no APK and no device guarantee.\n")
    print("LOW_SCORE_111_CROSS_TAB=PASS",len(cases),flush=True)
if __name__=="__main__":main()

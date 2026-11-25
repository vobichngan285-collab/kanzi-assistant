# Instrument the pinned engine without changing any math or weights.
from pathlib import Path
import sys
root=Path(sys.argv[1])
def change(path,old,new):
    p=root/path
    s=p.read_text()
    assert s.count(old)==1,(path,old,s.count(old))
    p.write_text(s.replace(old,new))
p="ggml/src/ggml-cpu/ggml-cpu.c"
needle="void ggml_compute_forward_mul_mat(\n"
change(p,needle,"""// Diagnostic counters: thread zero only, no tensor values recorded.
static uint64_t bs_generic_f16 = 0, bs_kai_f16 = 0;
void bs_record_f16_kai(void) { __atomic_fetch_add(&bs_kai_f16, 1, __ATOMIC_RELAXED); }
uint64_t bs_count_f16_generic(void) { return __atomic_load_n(&bs_generic_f16, __ATOMIC_RELAXED); }
uint64_t bs_count_f16_kai(void) { return __atomic_load_n(&bs_kai_f16, __ATOMIC_RELAXED); }
"""+needle)
change(p,"""                ggml_compute_forward_mul_mat(params, tensor);""","""                if (params->ith == 0 && tensor->src[0]->type == GGML_TYPE_F16) {
                    __atomic_fetch_add(&bs_generic_f16, 1, __ATOMIC_RELAXED);
                }
                ggml_compute_forward_mul_mat(params, tensor);""")
p="ggml/src/ggml-cpu/kleidiai/kleidiai.cpp"
change(p,"""                return compute_forward_fp16(params, dst);""","""                const bool ok = compute_forward_fp16(params, dst);
                if (ok && params->ith == 0) {
                    extern void bs_record_f16_kai_bridge();
                    bs_record_f16_kai_bridge();
                }
                return ok;""")
# The bridge declaration must use C linkage at namespace scope.
p=root/p
s=p.read_text()
s=s.replace("                    extern void bs_record_f16_kai_bridge();\n                    bs_record_f16_kai_bridge();","                    ::bs_record_f16_kai();")
s='extern "C" void bs_record_f16_kai(void);\n'+s
p.write_text(s)

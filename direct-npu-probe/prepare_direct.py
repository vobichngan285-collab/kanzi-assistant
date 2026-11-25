"""Independent host-only A/B of Android-style MiniCPM 4.7 assistant suffix."""
import pathlib,sys
p=pathlib.Path(sys.argv[1])/"tools/mtmd/mtmd-cli.cpp"
s=p.read_text()
def change(old,new):
    global s
    assert s.count(old)==1,old
    s=s.replace(old,new)
change('''    auto formatted = common_chat_format_single(ctx.tmpls.get(), ctx.chat_history,
        new_msg, new_msg.role == "user",
        ctx.use_jinja);''',r'''    auto formatted = common_chat_format_single(ctx.tmpls.get(), ctx.chat_history,
        new_msg, new_msg.role == "user",
        ctx.use_jinja);
    // Single-turn lab only. Both modes use identical Android-style ChatML.
    if (const char * mode = std::getenv("LAB_THINKING")) {
        GGML_ASSERT(ctx.chat_history.empty() && new_msg.role == "user");
        GGML_ASSERT(std::string(mode) == "on" || std::string(mode) == "off");
        formatted = "<|im_start|>user\n" + new_msg.content + "<|im_end|>\n<|im_start|>assistant\n";
        formatted += std::string(mode) == "on" ? "<think>\n" : "<think>\n\n</think>\n\n";
        fprintf(stderr, "LAB_THINKING=%s\n", mode);
    }''')
change('    std::string generated_text = common_detokenize(ctx.lctx, generated_tokens);',
       '    fprintf(stderr, "LAB_GENERATED_TOKENS=%zu\\n", generated_tokens.size());\n    std::string generated_text = common_detokenize(ctx.lctx, generated_tokens);')
# Measure the same prompt-eval + generation path, excluding model initialization.
change('''        if (eval_message(ctx, msg)) {
            return 1;
        }
        if (!g_is_interrupted && generate_response(ctx, n_predict)) {
            return 1;
        }''',r'''        const int64_t lab_t0 = ggml_time_ms();
        if (eval_message(ctx, msg)) {
            return 1;
        }
        fprintf(stderr, "LAB_PREFILL_MS=%" PRId64 "\n", ggml_time_ms() - lab_t0);
        if (!g_is_interrupted && generate_response(ctx, n_predict)) {
            return 1;
        }
        fprintf(stderr, "LAB_PREFILL_GENERATE_MS=%" PRId64 "\n", ggml_time_ms() - lab_t0);''')
# PRId64/stdio declarations explicitly available.
s='#include <cinttypes>\n#include <cstdio>\n'+s
p.write_text(s)

"""Add explicit timing independent of CLI logging verbosity."""
import pathlib, sys
root=pathlib.Path(sys.argv[1])
helper=root/"tools/mtmd/mtmd-helper.cpp"
s=helper.read_text()
needle=r'        LOG_INF("%s slice encoded in %" PRId64 " ms\n", name, ggml_time_ms() - t0);'
assert s.count(needle)==1
s=s.replace(needle,needle+"\n"+r'''        if (chunk_type == MTMD_INPUT_CHUNK_TYPE_IMAGE) {
            fprintf(stderr, "LAB_VISION_ENCODE_MS=%" PRId64 "\n", ggml_time_ms() - t0);
            fflush(stderr);
        }''')
helper.write_text(s)

"""Pin the probe to single-slice vision; preserve upstream inference and GPU selection."""
import pathlib, sys
root = pathlib.Path(sys.argv[1])
cli = root / "tools/mtmd/mtmd-cli.cpp"
source = cli.read_text()
needle = "        mparams.use_gpu          = params.mmproj_use_gpu;"
assert source.count(needle) == 1
cli.write_text(source.replace(needle, needle + "\n        mparams.image_max_slice_nums = 1;"))

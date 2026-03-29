from PIL import Image
import numpy as np
import sys

img = Image.open(sys.argv[1]).convert("RGB").resize((224, 224))
arr = np.array(img, dtype=np.int16)
arr = np.clip(arr - 128, -128, 127).astype(np.int8)
np.save(sys.argv[2], arr.reshape(-1))
print("saved", sys.argv[2], "shape", arr.shape)
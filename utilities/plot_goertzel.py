import matplotlib.pyplot as plt
import csv
import numpy as np

data = None
with open("../jqrtone/target/goertzel_test.csv", 'r') as f:
    data = list(csv.reader(f))

spectrum = np.array(data[1:], dtype=np.float)

fig, ax = plt.subplots()

#lines
for i,f in list(enumerate(data[0]))[1:]:
   line, = ax.plot(spectrum[:, 0], spectrum[:, i], '-o', label=f)

ax.legend()
plt.show()
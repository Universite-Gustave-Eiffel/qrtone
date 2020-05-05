import matplotlib.pyplot as plt
import csv
import numpy as np

data = None
# path = "../jqrtone/target/spectrum.csv"
path = "../out/build/x64-Debug/spectrum.csv"
with open(path, 'r') as f:
    data = list(csv.reader(f))

spectrum = np.array(data[1:], dtype=np.float)

fig, ax = plt.subplots()

#lines
for i,f in list(enumerate(data[0]))[1:][::2]:
   line, = ax.plot(spectrum[:, 0], spectrum[:, i], '-o', label=f)

#background lvls
for i,f in list(enumerate(data[0]))[2:][::2]:
    line, = ax.plot(spectrum[:, 0], spectrum[:, i], dashes=[6, 2], label=f)


# y(x) = a*(x-p)^2+b
# p = 0.44112027275489657
# y = 0.9997003184226267
# a = -0.034719765186309814
# #xvals = np.arange(spectrum[0, 0], spectrum[-1, 0],spectrum[1, 0] - spectrum[0, 0])
# xvals = np.arange(-5,5,0.05)
# line, = ax.plot(xvals, np.array([a*(x - p)**2 + y for x in xvals]), '-o', label="interp")
#
# # peaks
# for i,f in list(enumerate(data[0]))[1:][2::3]:
#     markers = np.array(np.argwhere(spectrum[:, i] > 0).flat)
#     line, = ax.plot(spectrum[markers, 0], spectrum[markers, i - 2], 'o', label=f)

ax.legend()
plt.show()
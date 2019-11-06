import matplotlib.pyplot as plt
import numpy as np

dat = np.fromfile("../jwarble/target/convolve.dat", dtype='>f8')


plt.plot(dat, label='linear')

plt.xlabel('x label')
plt.ylabel('y label')

plt.title("Simple Plot")

plt.legend()

plt.show()
# pip install pyserial
import serial
import matplotlib.pyplot as plt
import numpy as np

ser = serial.Serial('COM3', 9600)
ser.close()
ser.open()
fig, ax = plt.subplots()
max_values = 200
values = np.array([[]], dtype=np.float)
while True:
    data = ser.readline()
    column_id = 0
    for column in data.decode().split(","):
        if len(values) < column_id + 1:
            # new column
            values = np.insert(values, len(values), float(column), axis=0)
        else:
            values = np.insert(values, len(values[column_id]), float(column), axis=1)
        column_id += 1
    
    #plt.show()
    #plt.pause(0.001)

# pip install pyserial
import serial
import numpy as np
import matplotlib.pyplot as plt

ser = serial.Serial('COM3', 115200)
ser.close()
ser.open()
max_values = 100
values = []
graphs = []

# You probably won't need this if you're embedding things in a tkinter plot...
plt.ion()

fig, ax = plt.subplots()
pushed = 0
total_pushed = 0
while True:
    data = ser.readline()
    column_id = 0
    for column in data.decode().split(","):
        if len(values) == column_id:
            # new column
            values.append(np.array([], dtype=np.float))
            graphs.append(ax.plot(values[len(values) - 1], 'o-', label=str(column_id))[0])
        else:
            values[column_id] = np.append(values[column_id], float(column))
        column_id += 1
    pushed += 1
    total_pushed += 1
    if pushed == 1:
        pushed = 0
        for id_graph, graph in enumerate(graphs):
            if len(values[id_graph]) > max_values:
                values[id_graph] = values[id_graph][-max_values:]
            graph.set_ydata(values[id_graph])
            graph.set_xdata(np.arange(total_pushed - len(values[id_graph]), total_pushed))
        ax.relim()
        ax.autoscale_view()
        fig.canvas.draw()
        fig.canvas.flush_events()


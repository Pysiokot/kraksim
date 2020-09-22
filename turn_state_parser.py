from os import listdir
from os.path import isfile, join
import os

dataFile = open("stateAction.log", "r+")

actionStateMap = {}

for line in dataFile:
    lineSplitted = line.split(";")

    state = int(lineSplitted[1])
    if state not in actionStateMap:
        actionStateMap[state] = [0, 0, 0, 0, 0, 0, 0]

    actionStateMap[state][int(lineSplitted[2])] += 1


res = {}

for state, actions in actionStateMap.items():
    res[state] = actions.index(max(actions))

resFil = open("resSA.log", "w+")

for s, a in res.items():
    resFil.write(str(s))
    resFil.write(";")
    resFil.write(str(a))
    resFil.write("\n")


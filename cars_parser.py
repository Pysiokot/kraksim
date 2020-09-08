
from xml.dom import minidom as xd
from os import listdir
from os.path import isfile, join
import os
import math

def getCalculatedResult(files_res, items_count):
    result = {}

    for key, value in files_res.items():
        tmp_res = []

        for i in range(0, int(len(value))):
            tmp_res.append(value[i] / items_count)

        # result[key] = [(value[0] / items_count, value[1] / items_count), (value[2] / items_count, value[3] / items_count), (value[4] / items_count, value[5] / items_count)]
        result[key] = tmp_res

    return result


def addNewResult(data_holder, key_name, data):
    curr_value = data_holder[key_name]

    it_count = 0

    for i in range(len(data)):
        curr_value[i] += data[i]

    data_holder[key_name] = curr_value

N = 1

dirname = os.path.dirname(os.path.abspath(__file__))
mypath = os.path.join(dirname, 'output')
mypath = os.path.join(mypath, 'results')

onlyfiles = [f for f in listdir(mypath) if (isfile(join(mypath, f)) and os.path.splitext(f)[1] == '.xml')]

# print(listdir(mypath))

result = {}
files_results_sum = {}
dang_sits = {}

files_res = []

for onlyfile in onlyfiles:

    file_name_splited = onlyfile.split("_")

    key_name = file_name_splited[0]
    xmldoc = xd.parse(os.path.join(mypath, onlyfile))
    
    if key_name not in files_results_sum:
        dang_sits[key_name] = 0.0
        files_results_sum[key_name] = [0.0 for i in range(0, len(xmldoc.getElementsByTagName('link')))]


    dang_sits_for_file = xmldoc.getElementsByTagName('stats')
    for dang_sit in dang_sits_for_file:
        dang_sits[key_name] += int(dang_sit.attributes['dang_sits'].value)
        break

    itemlist = xmldoc.getElementsByTagName('period')

    car_count = 0

    file_res =  []

    for item in itemlist:
        attr_val = int(item.attributes['begin'].value)

        if(attr_val > 6900):
            continue

        curr_car_count = int(item.attributes['carCount'].value)
        car_count += curr_car_count
        
        if attr_val == 6900: # pomijamy kilka pierwszych wyników (rozgrzewka)

            # dump data
            file_res.append(car_count)
            car_count = 0

# if NAN -> pominac?

            
        
        # print(item.attributes['begin'].value)

    addNewResult(files_results_sum, key_name, file_res)


result = getCalculatedResult(files_results_sum, N)

f = open("result_ql_cars.csv", "w+")

itemlist = xmldoc.getElementsByTagName('link')

f.write(";")
for item in itemlist:
    f.write("car_count_" + item.attributes['from'].value + "_" + item.attributes['to'].value)
    f.write(";")

# f.write("research_id;average_vel_W_X;average_riding_vel_W_X;average_vel_S_X;average_riding_vel_S_X;average_vel_N_X;average_riding_vel_N_X")
f.write("\n")

for key, val in result.items():

    val_str = ""

    for value in val:
        val_str += str(value).replace('.',',')
        val_str += ";"

    f.write(key + ";" + val_str)
    f.write("\n")

for key, val in dang_sits.items():
    f.write(key + ";" + str(int(val/N)))
    f.write("\n")

    # print(result)

f.close()
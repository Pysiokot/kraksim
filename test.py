from xml.dom import minidom as xd
from os import listdir
from os.path import isfile, join
import os
import math

def getCalculatedResult(files_res, items_count):
    result = {}

    for key, value in files_res.items():
        result[key] = [(value[0] / items_count, value[1] / items_count), (value[2] / items_count, value[3] / items_count), (value[4] / items_count, value[5] / items_count)]

    return result


def addNewResult(data_holder, key_name, data):
    curr_value = data_holder[key_name]

    for i in range(len(data)):
        (x, y) = data[i]

        curr_value[2 * i] += x
        curr_value[(2 * i) + 1] += y

    data_holder[key_name] = curr_value

N = 50

dirname = os.path.dirname(os.path.abspath(__file__))
mypath = os.path.join(dirname, 'output')
mypath = os.path.join(mypath, 'results')

onlyfiles = [f for f in listdir(mypath) if (isfile(join(mypath, f)) and os.path.splitext(f)[1] == '.xml')]

# print(listdir(mypath))

result = {}
files_results_sum = {}

files_res = []

for onlyfile in onlyfiles:

    file_name_splited = onlyfile.split("_")

    key_name = file_name_splited[0]
    
    if key_name not in files_results_sum:

        files_results_sum[key_name] = [0.0, 0.0, 0.0, 0.0, 0.0, 0.0]

    xmldoc = xd.parse(os.path.join(mypath, onlyfile))
    itemlist = xmldoc.getElementsByTagName('period')

    av_item_ctr = 0
    arv_item_ctr = 0
    zeros = 0
    mean_av, mean_arv = 0.0, 0.0

    file_res =  []

    for item in itemlist:

        attr_val = int(item.attributes['begin'].value)
        if attr_val == 900: # pomijamy kilka pierwszych wyników (rozgrzewka)

            if zeros != 0 and zeros != 2 and zeros != 3 and zeros != 6:
                file_res.append((mean_av/av_item_ctr, mean_arv/arv_item_ctr))

            zeros += 1
                
            mean_av, mean_arv = 0.0, 0.0

            av_item_ctr = 0
            arv_item_ctr = 0
        elif attr_val < 900:
            continue

        if zeros == 2 or zeros == 3 or zeros == 6:
            continue


# if NAN -> pominac?
        av_vel = float(item.attributes['avg_velocity'].value.replace(',', '.'))
        av_r_vel = float(item.attributes['avg_riding_velocity'].value.replace(',', '.'))
        if not math.isnan(av_vel):
            av_item_ctr += 1
            mean_av += av_vel

        if not math.isnan(av_r_vel):
            arv_item_ctr += 1
            mean_arv += av_r_vel
            
        
        
        # print(item.attributes['begin'].value)

    addNewResult(files_results_sum, key_name, file_res)


result = getCalculatedResult(files_results_sum, N)

f = open("result.csv", "w+")

f.write("research_id;average_vel_W_X;average_riding_vel_W_X;average_vel_S_X;average_riding_vel_S_X;average_vel_N_X;average_riding_vel_N_X")
f.write("\n")

for key, val in result.items():

    val_str = ""

    for value in val:
        (x, y) = value
        val_str += str(x).replace('.',',')
        val_str += ";"
        val_str += str(y).replace('.',',')
        val_str += ";"

    f.write(key + ";" + val_str)
    f.write("\n")

    # print(result)

f.close()
from xml.dom import minidom as xd
from os import listdir
from os.path import isfile, join
import os
import math

def getCalculatedResult(files_res, items_count):
    result = {}

    for key, value in files_res.items():
        tmp_res = []

        for i in range(0, int(len(value)/2)):
            tmp_res.append((value[2 * i] / items_count, value[2 * i + 1] / items_count))

        # result[key] = [(value[0] / items_count, value[1] / items_count), (value[2] / items_count, value[3] / items_count), (value[4] / items_count, value[5] / items_count)]
        result[key] = tmp_res

    return result


def addNewResult(data_holder, key_name, data):
    curr_value = data_holder[key_name]

    it_count = 0

    for i in range(len(data)):
        (x, y) = data[i]

        if not str(x) == 'nan':
            curr_value[2 * i] += x
            curr_value[(2 * i) + 1] += y
        else:
            curr_value[2 * i] += 16.0
            curr_value[(2 * i) + 1] += 16.0

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
        files_results_sum[key_name] = [0.0 for i in range(0, len(xmldoc.getElementsByTagName('link')) * 2)]


    dang_sits_for_file = xmldoc.getElementsByTagName('stats')
    for dang_sit in dang_sits_for_file:
        dang_sits[key_name] += int(dang_sit.attributes['dang_sits'].value)
        break

    itemlist = xmldoc.getElementsByTagName('period')

    av_item_ctr = 0
    arv_item_ctr = 0
    mean_av, mean_arv = 0.0, 0.0

    file_res =  []

    for item in itemlist:
        attr_val = int(item.attributes['begin'].value)
        if attr_val < 900 or attr_val > 6300:
            continue

        av_vel = float(item.attributes['avg_velocity'].value.replace(',', '.'))
        av_r_vel = float(item.attributes['avg_riding_velocity'].value.replace(',', '.'))
        if not math.isnan(av_vel):
            av_item_ctr += 1
            mean_av += av_vel
            arv_item_ctr += 1
            mean_arv += av_r_vel

        if attr_val == 6300: # pomijamy kilka pierwszych wyników (rozgrzewka)

            # dump data
            mean = 0
            mean2 = 0
            if av_item_ctr != 0:
                mean = mean_av/av_item_ctr
                mean2 = mean_arv/arv_item_ctr
                file_res.append((mean, mean2))
            else:
                file_res.append(('nan', 'nan'))


            mean_av, mean_arv = 0.0, 0.0
            av_item_ctr = 0
            arv_item_ctr = 0

# if NAN -> pominac?

            
        
        # print(item.attributes['begin'].value)

    addNewResult(files_results_sum, key_name, file_res)


result = getCalculatedResult(files_results_sum, N)

f = open("result.csv", "w+")

itemlist = xmldoc.getElementsByTagName('link')

f.write(";")
for item in itemlist:
    f.write("average_vel_" + item.attributes['from'].value + "_" + item.attributes['to'].value)
    f.write(";")
    f.write("average_riding_vel_" + item.attributes['from'].value + "_" + item.attributes['to'].value)
    f.write(";")

# f.write("research_id;average_vel_W_X;average_riding_vel_W_X;average_vel_S_X;average_riding_vel_S_X;average_vel_N_X;average_riding_vel_N_X")
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

for key, val in dang_sits.items():
    f.write(key + ";" + str(int(val/N)))
    f.write("\n")

    # print(result)

f.close()
4trees PLC Core
===================================

One should enable the performance governor on the raspberry:

    sudo echo performance | sudo tee /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor
    sudo cat /sys/devices/system/cpu/cpufreq/policy0/scaling_governor
    sudo cat /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_cur_freq

#include <stdlib.h>
#include <strings.h>
#include <stdlib.h>
#include <stdio.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <unistd.h>
#include <stdio.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <string.h>
#include <stdio.h>
#include <sys/types.h>
#include <fcntl.h>
#include <syslog.h>
#include <time.h>
#include <signal.h>
#include <pthread.h>

#include <string>
#include <sstream>
#include <fstream>
#include <map>
#include <iostream>

static const char* SYSLOG_TAG = "Mesto";

void* doprocessing(void* arg);

pthread_mutex_t lock;

/**
 * port parameter
 * output file location/dir
 */
int main(int argc, char *argv[]) {
    openlog(SYSLOG_TAG, LOG_PERROR, 0);
    syslog(LOG_INFO, "starting up");

    if (pthread_mutex_init(&lock, NULL) != 0) {
        syslog(LOG_ERR, "ERROR mutex init failed");
        return 1;
    }

    struct sigaction sigchld_action;
    sigchld_action.sa_handler = SIG_DFL;
    sigchld_action.sa_flags = SA_NOCLDWAIT;
    sigaction(SIGCHLD, &sigchld_action, NULL);

    int sockfd, newsockfd, portno;
    socklen_t clilen;
    struct sockaddr_in serv_addr, cli_addr;

    /* First call to socket() function */
    sockfd = socket(AF_INET, SOCK_STREAM, 0);
    if (sockfd < 0) {
        syslog(LOG_ERR, "ERROR opening socket");
        exit(1);
    }
    /* Initialize socket structure */
    bzero((char *) &serv_addr, sizeof(serv_addr));
    portno = 5001;
    serv_addr.sin_family = AF_INET;
    serv_addr.sin_addr.s_addr = INADDR_ANY;
    serv_addr.sin_port = htons(portno);

    if (bind(sockfd, (struct sockaddr *) &serv_addr, sizeof(serv_addr)) < 0) {
        syslog(LOG_ERR, "ERROR on binding");
        exit(1);
    }
    listen(sockfd, 5);
    clilen = sizeof(cli_addr);
    while (1) {
        newsockfd = accept(sockfd, (struct sockaddr *) &cli_addr, &clilen);

        if (newsockfd < 0) {
            syslog(LOG_ERR, "ERROR on accept");
            exit(1);
        }

        pthread_t thread;
        int err = pthread_create(&thread, NULL, doprocessing, (void*) &newsockfd);
        if (0 != err) {
            syslog(LOG_ERR, "error creating thread; err: %X", err);
            exit(1);
        }
    }

    pthread_mutex_destroy(&lock);
    closelog();
}

std::map<std::string, std::string> gMap;

void* doprocessing(void* arg) {
    int n;
    char buffer[256];

    int sock = *((int*) arg);

    bzero(buffer, 256);

    n = read(sock, buffer, 255);
    if (n < 16) {
        syslog(LOG_ERR, "ERROR reading from socket");
        exit(1);
    }

    union myunion {
        char b[8];
        double d;
    } lat, lon;

    int offset = 0;

    //udn utf-8 string
    short udnLength = buffer[offset++] << 8;
    udnLength |= buffer[offset++];

    std::string id(buffer + offset, udnLength);
    offset += udnLength;

    //title utf-8 string
    short titleLength = buffer[offset++] << 8;
    titleLength |= buffer[offset++];

    std::string title(buffer + offset, titleLength);
    offset += titleLength;

    lon.b[7] = buffer[offset++];
    lon.b[6] = buffer[offset++];
    lon.b[5] = buffer[offset++];
    lon.b[4] = buffer[offset++];
    lon.b[3] = buffer[offset++];
    lon.b[2] = buffer[offset++];
    lon.b[1] = buffer[offset++];
    lon.b[0] = buffer[offset++];

    lat.b[7] = buffer[offset++];
    lat.b[6] = buffer[offset++];
    lat.b[5] = buffer[offset++];
    lat.b[4] = buffer[offset++];
    lat.b[3] = buffer[offset++];
    lat.b[2] = buffer[offset++];
    lat.b[1] = buffer[offset++];
    lat.b[0] = buffer[offset++];

    std::stringstream ss;
    ss << "{\"lon\":" << lon.d << ",\"lat\":" << lat.d << ",\"title\":\"" << title << "\"}";
    std::string sss = ss.str();

    pthread_mutex_lock(&lock);
    gMap[title] = sss;
    pthread_mutex_unlock(&lock);

    std::ofstream file;
    file.open("/opt/share/httpd/coord.json", std::ios::out | std::ios::trunc);
    if (file.is_open()) {
        file << "\"devices\":[" << std::endl;
        for (auto& item : gMap) {
            std::cout << item.first << "   " << item.second << std::endl;
            file << item.second << ',' << std::endl;
        }
        file << "]" << std::endl;
        file.close();
    } else {
        syslog(LOG_ERR, "cannot open output file");
        exit(1);
    }

    time_t t = time(NULL);
    struct tm tm = *localtime(&t);
    printf("%d-%d-%d %d:%d:%d\n", tm.tm_year + 1900, tm.tm_mon + 1, tm.tm_mday, tm.tm_hour, tm.tm_min, tm.tm_sec);
    syslog(LOG_INFO, "Here is the message: %f, %f", lat.d, lon.d);

    n = write(sock, "gotit", 5);
    close(sock);
    return nullptr;
}


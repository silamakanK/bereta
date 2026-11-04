package com.bereta.admin;

public record AdminOverviewResponse(long associations, long activeAssociations, long users,
                                    long activeUsers, long primaryFunds, long localFunds,
                                    long funds, long members, long administrators) { }
